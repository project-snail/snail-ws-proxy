package com.snail.client.accept;

import com.snail.client.properties.WpPortClientProperties;
import com.snail.core.handler.DataHandler;
import com.snail.client.util.WpSessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.*;
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

    private WpPortClientProperties wpPortClientProperties;

    private DataHandler dataHandler;

    private WpCommonAccept wpCommonAccept;

    public LinkAcceptHandler(WpPortClientProperties wpPortClientProperties) throws IOException {
        this.wpPortClientProperties = wpPortClientProperties;
        dataHandler = new DataHandler();
        createSessionFun = WpSessionUtil.createSessionFun(dataHandler, wpPortClientProperties.getServerUrl());
        for (WpPortClientProperties.PortForwarding portForwarding : wpPortClientProperties.getPortForwardingList()) {
            wpCommonAccept = new WpCommonAccept(
                InetAddress.getByName(portForwarding.getBindAddr()),
                portForwarding.getBindPort(),
                selectionKey -> handlerSelect(selectionKey, portForwarding)
            );
            wpCommonAccept.startBind();
        }

    }

    private void handlerSelect(SelectionKey selectionKey, WpPortClientProperties.PortForwarding portForwarding) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
        Session session = createSessionFun.get();
//                    绑定远程连接
        dataHandler.writeRemoteInfoToSessionAndRegister(
            session,
            portForwarding.getRemoteAddress(),
            portForwarding.getRemotePort(),
            serverSocketChannel.accept()
        );
    }


}
