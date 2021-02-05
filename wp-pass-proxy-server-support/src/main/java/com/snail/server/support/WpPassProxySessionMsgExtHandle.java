package com.snail.server.support;

import com.snail.client.accept.WpCommonAccept;
import com.snail.core.config.SessionMsgExtHandleRegister;
import com.snail.core.handler.DataHandler;
import com.snail.core.handler.WpSessionMsgExtHandle;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.websocket.Session;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.server.support
 * @Description:
 * @date: 2021/02/03
 */
@Slf4j
@ConditionalOnBean(SessionMsgExtHandleRegister.class)
public class WpPassProxySessionMsgExtHandle {

    private final Map<Session, PassProxyInfo> passProxyInfoMap = new ConcurrentHashMap<>();

    private final Map<Integer, ChannelInfo> socketChannelMap = new ConcurrentHashMap<>();

    private final Set<ChannelInfo> channelClearSet = new ConcurrentSkipListSet<>(Comparator.comparing(ChannelInfo::getLinkTime));

    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(
        1,
        r -> new Thread(r, "PassProxyChannelClearScheduled")
    );

    private AtomicInteger indexGen = new AtomicInteger();

    @Autowired
    public WpPassProxySessionMsgExtHandle(SessionMsgExtHandleRegister sessionMsgExtHandleRegister) {
        sessionMsgExtHandleRegister.registerSessionMsgExtHandle(new AcceptSessionMsgExtHandle());
        sessionMsgExtHandleRegister.registerSessionMsgExtHandle(new LinkSessionMsgExtHandle());
//        定时清理未连接channel
        scheduledExecutorService.scheduleWithFixedDelay(this::clearUnLinkChannel, 5, 5, TimeUnit.SECONDS);
    }

    class AcceptSessionMsgExtHandle implements WpSessionMsgExtHandle {

        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {

            byte[] localBindAddr = new byte[byteBuffer.getInt()];
            byteBuffer.get(localBindAddr);
            int localBindPort = byteBuffer.getInt();

            PassProxyInfo passProxyInfo = PassProxyInfo.builder()
                .session(session)
                .localBindAddr(new String(localBindAddr, StandardCharsets.UTF_8))
                .localBindPort(localBindPort)
                .dataHandler(dataHandler)
                .build();

            passProxyInfoMap.put(session, passProxyInfo);
            WpCommonAccept wpCommonAccept;
            try {
                wpCommonAccept = startBind(passProxyInfo);
                wpCommonAccept.startBind();
                passProxyInfo.setWpCommonAccept(wpCommonAccept);
            } catch (IOException e) {
                throw new RuntimeException("反代，绑定本地端口异常", e);
            }

//            注册关闭session时的回调
            dataHandler.registerCloseSessionConsumer(
                session,
                WpPassProxySessionMsgExtHandle.this::closeBind
            );

            log.trace("开启 pass-proxy {} --> {}", passProxyInfo, session.getId());

        }

        private WpCommonAccept startBind(PassProxyInfo passProxyInfo) throws IOException {
            return new WpCommonAccept(
                InetAddress.getByName(passProxyInfo.getLocalBindAddr()),
                passProxyInfo.getLocalBindPort(),
                selectionKey -> this.handlerAccept(passProxyInfo, selectionKey)
            );
        }

        private void handlerAccept(PassProxyInfo passProxyInfo, SelectionKey selectionKey) {

            SocketChannel socketChannel = null;
            try {
                socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
            } catch (IOException e) {
                e.printStackTrace();
            }

            int index = indexGen.incrementAndGet();

            ChannelInfo channelInfo = ChannelInfo.builder()
                .index(index)
                .socketChannel(socketChannel)
                .sessionRef(new WeakReference<>(passProxyInfo.getSession()))
                .linkTime(System.currentTimeMillis())
                .build();
            socketChannelMap.put(index, channelInfo);
            channelClearSet.add(channelInfo);

            DataHandler dataHandler = passProxyInfo.getDataHandler();

            ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 4);
            byteBuffer.put((byte) 3);
            byteBuffer.putInt(index);
            byteBuffer.flip();

            dataHandler.doWriteRawToSession(passProxyInfo.getSession(), byteBuffer);
        }

        @Override
        public byte handleType() {
            return 2;
        }

    }

    class LinkSessionMsgExtHandle implements WpSessionMsgExtHandle {

        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {
            int index = byteBuffer.getInt();
            ChannelInfo channelInfo = socketChannelMap.remove(index);
            if (channelInfo == null) {
                throw new RuntimeException("该链接已不存在");
            }
            channelClearSet.remove(channelInfo);
            if (channelInfo.getSocketChannel() == null || !channelInfo.getSocketChannel().isOpen()) {
                throw new RuntimeException("该链接已被关闭");
            }
            try {
                dataHandler.registerSession(session, channelInfo.getSocketChannel());
            } catch (IOException e) {
                throw new RuntimeException("绑定链接时异常", e);
            }
        }

        @Override
        public byte handleType() {
            return 3;
        }

    }

    private void closeBind(Session session) {
        PassProxyInfo passProxyInfo = passProxyInfoMap.remove(session);
        Optional.ofNullable(passProxyInfo)
            .map(PassProxyInfo::getWpCommonAccept)
            .ifPresent(WpCommonAccept::close);
        log.trace("关闭pass-proxy {} --> {}", passProxyInfo, session.getId());
    }

    /**
     * 清理长时间未连接的channel
     */
    private void clearUnLinkChannel() {
        Iterator<ChannelInfo> iterator = channelClearSet.iterator();
        long currentTimeMillis = System.currentTimeMillis();
        while (iterator.hasNext()) {
            ChannelInfo channelInfo = iterator.next();
            if (channelInfo == null) {
                continue;
            }
            if (channelInfo.getLinkTime() - currentTimeMillis < 60 * 1000) {
                break;
            }
            socketChannelMap.remove(channelInfo.getIndex());
            iterator.remove();
            if (channelInfo.getSocketChannel().isOpen()) {
                try {
                    channelInfo.getSocketChannel().close();
                } catch (IOException e) {
                    log.warn("关闭长时间未连接channel时异常", e);
                }
            }
        }
    }

    @Data
    @Builder
    private static class PassProxyInfo {

        private Session session;

        private DataHandler dataHandler;

        private String localBindAddr;

        private int localBindPort;

        private WpCommonAccept wpCommonAccept;

        @Override
        public String toString() {
            return String.format("%s:%d", localBindAddr, localBindPort);
        }
    }

    @Data
    @Builder
    private static class ChannelInfo {

        private SocketChannel socketChannel;

        private WeakReference<Session> sessionRef;

        private long linkTime;

        private int index;

    }

}
