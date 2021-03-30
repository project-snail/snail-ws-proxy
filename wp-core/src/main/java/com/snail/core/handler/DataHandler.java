package com.snail.core.handler;

import com.snail.core.holder.SessionHolder;
import com.snail.core.type.MsgTypeEnums;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.SendHandler;
import javax.websocket.Session;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.core.handler
 * @Description:
 * @date: 2021/02/01
 */
@Data
@Slf4j
public class DataHandler {

    private static final Map<Session, SessionHolder> sessionHolderMap = new ConcurrentHashMap<>();

    public static final AttributeKey<WeakReference<SessionHolder>> ATTACHMENT_ATTRIBUTE_KEY =
        AttributeKey.valueOf("attachment");

    private WpSelectCommonHandler wpSelectCommonHandler;

    private static final Map<Byte, WpSessionMsgExtHandle> sessionMsgExtHandleMap = new ConcurrentHashMap<>();

    //    关闭session时的回调列表
    private static final Map<Session, Collection<Consumer<Session>>> sessionCloseConsumerMap = new ConcurrentHashMap<>();

    private Executor sessionMsgExecutor;

    public DataHandler(int corePoolSize) {
        this.wpSelectCommonHandler = new WpSelectCommonHandler(
            corePoolSize,
            WpSelectCommonHandler.MsgConsumer.build(
                this::doHandle,
                context -> Optional.ofNullable(context.channel().attr(ATTACHMENT_ATTRIBUTE_KEY).get())
                    .map(WeakReference::get)
                    .map(SessionHolder::getSession)
                    .ifPresent(this::closeSession),
                context -> Optional.ofNullable(context.channel().attr(ATTACHMENT_ATTRIBUTE_KEY).get())
                    .map(WeakReference::get)
                    .map(SessionHolder::getSession)
                    .ifPresent(this::closeSession)
            )
        );
        this.sessionMsgExecutor = new ThreadPoolExecutor(
            corePoolSize, corePoolSize, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(corePoolSize * 10, true),
            new DefaultThreadFactory("sessionMsgExecutor"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public DataHandler() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    public void handleSessionMsg(Session session, ByteBuffer byteBuffer) {
        sessionMsgExecutor.execute(() -> doHandleSessionMsg(session, byteBuffer));
    }

    public void doHandleSessionMsg(Session session, ByteBuffer byteBuffer) {
        byte msgType = byteBuffer.get();
        if (msgType == 1) {
            writeToRemote(session, byteBuffer);
        } else if (msgType == 0) {
            registerSession(session, byteBuffer);
        }
//        额外的消息处理 方便扩展
        WpSessionMsgExtHandle wpSessionMsgExtHandle = sessionMsgExtHandleMap.get(msgType);
        if (wpSessionMsgExtHandle != null) {
            wpSessionMsgExtHandle.handleSessionMsg(session, byteBuffer, this);
        }
    }

    private void registerSession(Session session, ByteBuffer byteBuffer) {
        byte[] remoteAddr = new byte[byteBuffer.getInt()];
        byteBuffer.get(remoteAddr);
        int remotePort = byteBuffer.getInt();
        registerSession(session, new String(remoteAddr, StandardCharsets.UTF_8), remotePort);
    }

    /**
     * 使session与一个远程地址绑定
     *
     * @param session    sessions
     * @param remoteAddr 远程地址
     * @param remotePort 远程端口
     * @return SessionHolder
     */
    public SessionHolder registerSession(Session session, String remoteAddr, int remotePort) {
        SessionHolder sessionHolder = new SessionHolder(session, remoteAddr, remotePort);
        return doRegisterSession(session, sessionHolder);
    }

    /**
     * 使session与一个channel进行绑定
     *
     * @param session sessions
     * @param channel channel
     * @return SessionHolder
     */
    public SessionHolder registerSession(Session session, Channel channel) {
        SessionHolder sessionHolder = new SessionHolder(session, this, channel);
        doRegisterSession(session, sessionHolder);
        channel.read();
        return sessionHolder;
    }

    /**
     * 使session与一个byteBufferConsumer进行绑定
     *
     * @param session            sessions
     * @param byteBufferConsumer 自定义的数据接收器
     * @return SessionHolder
     */
    public SessionHolder registerSession(Session session, Consumer<ByteBuffer> byteBufferConsumer) {
        SessionHolder sessionHolder = new SessionHolder(session, byteBufferConsumer);
        doRegisterSession(session, sessionHolder);
        return sessionHolder;
    }

    private SessionHolder doRegisterSession(Session session, SessionHolder sessionHolder) {
        sessionHolderMap.put(session, sessionHolder);
        log.trace("注册通道 session --> {}, id: {}", sessionHolder.getLinkInfo(), session.getId());
        return sessionHolder;
    }

    /**
     * 写入信息至远程端
     */
    public void writeToRemote(Session session, ByteBuffer byteBuffer) {
        SessionHolder sessionHolder = sessionHolderMap.get(session);
        if (sessionHolder == null) {
            log.debug("该session未注册 id: {}", session.getId());
            closeSession(session);
            return;
        }
        doWriteToRemote(sessionHolder, byteBuffer);
        log.trace("写入数据 session --> {}", sessionHolder.getLinkInfo());
    }

    /**
     * 写入信息至session端
     */
    public void writeToSession(SessionHolder sessionHolder, ByteBuffer byteBuffer, SendHandler sendHandler) {
        Session session = sessionHolder.getSession();
        log.trace("写入数据 {} --> session ", sessionHolder.getLinkInfo());
        doWriteToSession(session, byteBuffer, sendHandler);
    }

    /**
     * 写入远程连接地址至session 代表这个session的对方连接地址
     *
     * @param addr 远程连接地址
     * @param port 远程连接端口
     */
    public void writeRemoteInfoToSessionAndRegister(Session session, String addr, int port, Channel channel) {
        byte[] addrBytes = addr.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + addrBytes.length + 4);
        buffer.put((byte) MsgTypeEnums.CONNECTION.ordinal());
        buffer.putInt(addrBytes.length);
        buffer.put(addrBytes);
        buffer.putInt(port);
        buffer.flip();
        try {
            doWriteRawToSession(
                session,
                buffer,
                result -> {
                    SessionHolder sessionHolder = registerSession(session, channel);
                    log.trace(
                        "写入远程端链接数据 {} link {}:{} --> session ",
                        sessionHolder.getLinkInfo(), addr, port
                    );
                }
            );
        } catch (Exception e) {
            closeSession(session);
            log.warn("写入数据到客户端连接时失败", e);
        }
    }

    private void doWriteToRemote(SessionHolder sessionHolder, ByteBuffer byteBuffer) {
        SessionHolder.LinkInfo linkInfo = sessionHolder.getLinkInfo();

        if (!linkInfo.isActive()) {
            synchronized (sessionHolder) {
                if (!linkInfo.isActive()) {
//                半初始化状态下追加数据 通道链接操作完成后flush
                    if (SessionHolder.LinkState.INIT.equals(linkInfo.getState())) {
                        writeToChannel(sessionHolder.getChannel(), Channel::write, byteBuffer);
                    }
                    try {
                        ChannelFuture channelFuture = sessionHolder.activeRemoteStream(this);
                        channelFuture.addListener(
                            (ChannelFutureListener) future -> {
                                if (!future.isSuccess()) {
                                    closeSession(sessionHolder.getSession());
                                    return;
                                }
                                channelFuture.channel().flush();
                                channelFuture.channel().read();
                                linkInfo.setState(SessionHolder.LinkState.ACTIVE);
                                log.trace("通道初始化完成 flush数据 session --> {}", sessionHolder.getLinkInfo());
                            }
                        );
                        channelFuture.channel().write(Unpooled.copiedBuffer(byteBuffer));
                        log.trace("初始化通道 首次写入数据 session --> {}", sessionHolder.getLinkInfo());
                        return;
                    } catch (Exception e) {
                        closeSession(sessionHolder.getSession());
                        throw new RuntimeException("开启远程连接失败", e);
                    }
                }
            }
        }

//        使用自定义的数据消费者
        if (linkInfo.isUserConsumer()) {
            linkInfo.getByteBufferConsumer().accept(byteBuffer);
        } else {
            Channel channel = sessionHolder.getChannel();

            if (!channel.isOpen()) {
                closeSession(sessionHolder.getSession());
                return;
            }
            writeToChannel(channel, Channel::writeAndFlush, byteBuffer);
        }


    }

    private ChannelFuture writeToChannel(Channel channel, BiFunction<Channel, ByteBuf, ChannelFuture> writeFun, ByteBuffer byteBuffer) {

        ChannelFuture channelFuture = writeFun.apply(channel, Unpooled.copiedBuffer(byteBuffer));

//        判断是否写 (高低水位控制)
        if (channel.isWritable()) {
            return channelFuture;
        }

        try {
            channelFuture.sync();
        } catch (InterruptedException ignored) {
        }

        return channelFuture;
    }

    public void doWriteToSession(Session session, ByteBuffer byteBuffer, SendHandler sendHandler) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + byteBuffer.remaining());
        buffer.put((byte) MsgTypeEnums.SEND.ordinal());
        buffer.put(byteBuffer);
        buffer.flip();
        doWriteRawToSession(session, buffer, sendHandler);
    }

    public void doWriteRawToSession(Session session, ByteBuffer buffer, SendHandler sendHandler) {
        if (sendHandler == null) {
            session.getAsyncRemote().sendBinary(buffer);
        } else {
            session.getAsyncRemote().sendBinary(buffer, sendHandler);
        }
//        try {
//            session.getBasicRemote().sendBinary(buffer);
//        } catch (IOException e) {
//            throw new RuntimeException("写入数据到客户端连接时失败", e);
//        }
    }

    public void doHandle(ChannelHandlerContext context, ByteBuf msg) {

        Channel channel = context.channel();

        WeakReference<SessionHolder> reference = channel.attr(ATTACHMENT_ATTRIBUTE_KEY).get();

        SessionHolder sessionHolder;
//        该channel对应的sessionHolder已经没了，说明该结束这个链接
        if (reference == null || (sessionHolder = reference.get()) == null) {
            channel.close();
            return;
        }

        if (!sessionHolder.getSession().isOpen()) {
            closeSession(sessionHolder.getSession());
            return;
        }

        writeToSession(
            sessionHolder,
            msg.nioBuffer(),
            result -> channel.read()
        );

    }

    public void closeSession(Session session) {

        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }

        // 回调关闭session
        Collection<Consumer<Session>> consumerList = sessionCloseConsumerMap.remove(session);
        if (consumerList != null) {
            consumerList.forEach(
                sessionConsumer -> {
                    try {
                        sessionConsumer.accept(session);
                    } catch (Exception e) {
                        log.error("session关闭回调异常", e);
                    }
                }
            );
        }

        SessionHolder sessionHolder = sessionHolderMap.remove(session);
        if (sessionHolder == null) {
            return;
        }
        log.trace(" {} -- 关闭 id: {}", sessionHolder.getLinkInfo(), session.getId());
        Channel channel = sessionHolder.getLinkInfo().getChannel();
        if (channel != null) {
            channel.close();
        }

    }

    /**
     * 注册关闭session时的回调
     */
    public void registerCloseSessionConsumer(Session session, Consumer<Session> sessionConsumer) {
        Collection<Consumer<Session>> consumers = sessionCloseConsumerMap.computeIfAbsent(
            session,
            s -> new ConcurrentLinkedQueue<>()
        );
        consumers.add(sessionConsumer);
    }

    /**
     * 注册额外的消息处理器
     *
     * @param wpSessionMsgExtHandle 消息处理器实例
     */
    public static void registerSessionMsgExtHandle(WpSessionMsgExtHandle wpSessionMsgExtHandle) {
        WpSessionMsgExtHandle absent = sessionMsgExtHandleMap.putIfAbsent(
            wpSessionMsgExtHandle.handleType(),
            wpSessionMsgExtHandle
        );
        if (absent != null) {
            throw new RuntimeException(String.format("此注册器类类型已包含 --> %s", sessionMsgExtHandleMap));
        }
    }

    public void registerChannel(Channel channel, SessionHolder sessionHolder) {
        channel.attr(ATTACHMENT_ATTRIBUTE_KEY).set(new WeakReference<>(sessionHolder));
    }

    public ChannelFuture open(InetSocketAddress inetSocketAddress, Consumer<Channel> channelConsumer) {
        ChannelFuture channelFuture = getWpSelectCommonHandler().open(inetSocketAddress);
        if (channelConsumer != null) {
            channelConsumer.accept(channelFuture.channel());
        }
        return channelFuture;
    }
}
