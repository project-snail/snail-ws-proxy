package com.snail.core.handler;

import com.snail.core.holder.SessionHolder;
import com.snail.core.type.MsgTypeEnums;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
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

    private WpSelectCommonHandler wpSelectCommonHandler;

    private static final Map<Byte, WpSessionMsgExtHandle> sessionMsgExtHandleMap = new ConcurrentHashMap<>();

    //    关闭session时的回调列表
    private static final Map<Session, Collection<Consumer<Session>>> sessionCloseConsumerMap = new ConcurrentHashMap<>();

    public DataHandler() throws IOException {
        this.wpSelectCommonHandler = new WpSelectCommonHandler(
            WpSelectCommonHandler.SelectionKeyConsumer.build(
                this::doHandle,
                selectionKey -> Optional.ofNullable((WeakReference<SessionHolder>) selectionKey.attachment())
                    .map(WeakReference::get)
                    .map(SessionHolder::getSession)
                    .ifPresent(this::closeSession)
            )
        );
    }

    public void handleSessionMsg(Session session, ByteBuffer byteBuffer) {
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
     * @param session       sessions
     * @param socketChannel channel
     * @return SessionHolder
     * @throws IOException 注册selector异常
     */
    public SessionHolder registerSession(Session session, SocketChannel socketChannel) throws IOException {
        SessionHolder sessionHolder = new SessionHolder(session, this, socketChannel);
        return doRegisterSession(session, sessionHolder);
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
        log.trace("写入数据  session --> {}", sessionHolder.getLinkInfo());
    }

    /**
     * 写入信息至session端
     */
    public void writeToSession(SessionHolder sessionHolder, ByteBuffer byteBuffer) {
        Session session = sessionHolder.getSession();
        log.trace("写入数据 {} --> session ", sessionHolder.getLinkInfo());
        doWriteToSession(session, byteBuffer);
    }

    /**
     * 写入远程连接地址至session 代表这个session的对方连接地址
     *
     * @param addr 远程连接地址
     * @param port 远程连接端口
     */
    public void writeRemoteInfoToSessionAndRegister(Session session, String addr, int port, SocketChannel socketChannel) throws IOException {
        byte[] addrBytes = addr.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + addrBytes.length + 4);
        buffer.put((byte) MsgTypeEnums.CONNECTION.ordinal());
        buffer.putInt(addrBytes.length);
        buffer.put(addrBytes);
        buffer.putInt(port);
        buffer.flip();
        try {
            session.getBasicRemote().sendBinary(buffer);
            SessionHolder sessionHolder = registerSession(session, socketChannel);
            log.trace(
                "写入远程端链接数据 {} link {}:{} --> session ",
                sessionHolder.getLinkInfo(), addr, port
            );
        } catch (IOException e) {
            throw new RuntimeException("写入数据到客户端连接时失败", e);
        }
    }

    private void doWriteToRemote(SessionHolder sessionHolder, ByteBuffer byteBuffer) {
        SessionHolder.LinkInfo linkInfo = sessionHolder.getLinkInfo();
        if (!linkInfo.isActive()) {
            try {
                sessionHolder.activeRemoteStream(this);
            } catch (IOException e) {
                closeSession(sessionHolder.getSession());
                throw new RuntimeException("开启远程连接失败", e);
            }
        }
        try {
            sessionHolder.getSocketChannel().write(byteBuffer);
        } catch (IOException e) {
            throw new RuntimeException("写入数据到远程连接时失败", e);
        }
    }

    private void doWriteToSession(Session session, ByteBuffer byteBuffer) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + byteBuffer.remaining());
        buffer.put((byte) MsgTypeEnums.SEND.ordinal());
        buffer.put(byteBuffer);
        buffer.flip();
        doWriteRawToSession(session, buffer);
    }

    public void doWriteRawToSession(Session session, ByteBuffer buffer) {
        try {
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException e) {
            throw new RuntimeException("写入数据到客户端连接时失败", e);
        }
    }

    private void doHandle(SelectionKey selectionKey) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        SocketChannel channel = (SocketChannel) selectionKey.channel();
        WeakReference<SessionHolder> sessionHolderWeakReference = (WeakReference<SessionHolder>) selectionKey.attachment();
        SessionHolder sessionHolder;
//        该channel对应的sessionHolder已经没了，说明该结束这个链接
        if (sessionHolderWeakReference == null || (sessionHolder = sessionHolderWeakReference.get()) == null) {
            channel.close();
            selectionKey.cancel();
            return;
        }
        int read;
        while ((read = channel.read(byteBuffer)) > 0) {
            byteBuffer.flip();
            writeToSession(sessionHolder, byteBuffer);
        }
        if (read == -1) {
            closeSession(sessionHolder.getSession());
        }
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
        SelectionKey selectionKey = sessionHolder.getLinkInfo().getSelectionKey();
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        try {
            SocketChannel socketChannel = sessionHolder.getSocketChannel();
            if (socketChannel != null) {
                socketChannel.close();
            }
        } catch (IOException ignored) {
        }

    }

    /**
     * 注册SelectableChannel到selector
     *
     * @param selectableChannel    channel
     * @param ops                  监听的类型
     * @param att                  自定义属性
     * @param selectionKeyConsumer 接受selectionKey的回到
     */
    public void registerChannel(SelectableChannel selectableChannel, int ops, Object att, Consumer<SelectionKey> selectionKeyConsumer) throws IOException {
        this.wpSelectCommonHandler.registerChannel(selectableChannel, ops, att, selectionKeyConsumer);
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

}
