package com.snail.client.accept;

import com.snail.client.properties.WpPassProxyClientProperties;
import com.snail.core.handler.DataHandler;
import com.snail.client.util.WpSessionUtil;
import com.snail.core.handler.WpSessionMsgExtHandle;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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

    private Executor executor = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory("pass-proxy-bind", false)
    );

    public LinkAcceptHandler(WpPassProxyClientProperties wpPassProxyClientProperties) {
        this.wpPassProxyClientProperties = wpPassProxyClientProperties;
        dataHandler = new DataHandler();
        DataHandler.registerSessionMsgExtHandle(new LinkSessionMsgExtHandle());
        createSessionFun = WpSessionUtil.createSessionFun(dataHandler, wpPassProxyClientProperties.getServerUrl());
        executor.execute(this::startBind);
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
        dataHandler.registerCloseSessionConsumer(
            session,
            closeSession -> executor.execute(this::startBind)
        );

    }

    class LinkSessionMsgExtHandle implements WpSessionMsgExtHandle {

        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {
            int index = byteBuffer.getInt();

            Session linkSession = createSessionFun.get();

            ChannelFuture channelFuture = dataHandler.open(
                new InetSocketAddress(
                    wpPassProxyClientProperties.getLocalAddr(),
                    wpPassProxyClientProperties.getLocalPort()
                ),
                null
            );

            channelFuture.channel().config().setAutoRead(false);

            channelFuture.addListener(
                (ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        return;
                    }

                    dataHandler.registerSession(linkSession, channelFuture.channel());

                    ByteBuffer linkInfo = ByteBuffer.allocate(1 + 4);
                    linkInfo.put((byte) 3);
                    linkInfo.putInt(index);
                    linkInfo.flip();

                    dataHandler.doWriteRawToSession(linkSession, linkInfo, null);
                }
            );

        }

        @Override
        public byte handleType() {
            return 3;
        }

    }


}
