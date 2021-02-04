package com.snail.client.accept;

import com.snail.client.properties.WpPassProxyClientProperties;
import com.snail.core.handler.DataHandler;
import com.snail.client.util.WpSessionUtil;
import com.snail.core.handler.WpSessionMsgExtHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client.accept
 * @Description:
 * @date: 2021/02/01
 */
@Slf4j
@Component
public class LinkAcceptHandler {

    private final Supplier<Session> createSessionFun;

    private WpPassProxyClientProperties wpPassProxyClientProperties;

    private DataHandler dataHandler;

    public LinkAcceptHandler(WpPassProxyClientProperties wpPassProxyClientProperties) throws IOException {
        this.wpPassProxyClientProperties = wpPassProxyClientProperties;
        dataHandler = new DataHandler();
        DataHandler.registerSessionMsgExtHandle(new LinkSessionMsgExtHandle());
        createSessionFun = WpSessionUtil.createSessionFun(dataHandler, wpPassProxyClientProperties.getServerUrl());
        startBind();
    }

    private void startBind() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        doBind();
        log.info("开启proxy-pass 启动新session");
    }

    private void doBind() {
        Session session = createSessionFun.get();

        byte[] remoteBindAddr = wpPassProxyClientProperties.getRemoteBindAddr().getBytes(StandardCharsets.UTF_8);
        int remoteBindPort = wpPassProxyClientProperties.getRemoteBindPort();

        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 4 + 4 + remoteBindAddr.length);
        byteBuffer.put((byte) 2);
        byteBuffer.putInt(remoteBindAddr.length);
        byteBuffer.put(remoteBindAddr);
        byteBuffer.putInt(remoteBindPort);
        byteBuffer.flip();

        try {
            session.getBasicRemote().sendBinary(byteBuffer);
        } catch (IOException e) {
            throw new RuntimeException("发送初始化消息异常", e);
        }

//        session关闭后重复链接
        dataHandler.registerCloseSessionConsumer(session, closeSession -> startBind());
    }

    class LinkSessionMsgExtHandle implements WpSessionMsgExtHandle {

        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {
            int index = byteBuffer.getInt();

            Session linkSession = createSessionFun.get();
            ByteBuffer linkInfo = ByteBuffer.allocate(1 + 4);
            linkInfo.put((byte) 3);
            linkInfo.putInt(index);
            linkInfo.flip();

            SocketChannel socketChannel;
            try {
                socketChannel = open();
                dataHandler.registerSession(linkSession, socketChannel);
                linkSession.getBasicRemote().sendBinary(linkInfo);
            } catch (IOException e) {
                throw new RuntimeException("proxy-pass: session访问本地端口异常", e);
            }

        }

        private SocketChannel open() throws IOException {
            SocketChannel socketChannel = SocketChannel.open(
                new InetSocketAddress(
                    wpPassProxyClientProperties.getLocalAddr(),
                    wpPassProxyClientProperties.getLocalPort()
                )
            );
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE)
                .setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
            dataHandler.registerChannel(
                socketChannel,
                SelectionKey.OP_READ,
                new WeakReference<>(this),
                null
            );
            return socketChannel;
        }

        @Override
        public byte handleType() {
            return 3;
        }
    }


}
