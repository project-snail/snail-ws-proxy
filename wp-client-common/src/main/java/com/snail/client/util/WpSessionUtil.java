package com.snail.client.util;


import com.snail.core.handler.DataHandler;
import com.snail.client.endpoint.WpClientEndpoint;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client.util
 * @Description:
 * @date: 2021/02/02
 */
public enum WpSessionUtil {
    ;
    private static WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    public static Session createSession(DataHandler dataHandler, String serverUrl) throws IOException, DeploymentException {
        return container.connectToServer(
            new WpClientEndpoint(dataHandler),
            URI.create(serverUrl)
        );
    }

    public static Supplier<Session> createSessionFun(DataHandler dataHandler, String serverUrl) {
        return () -> {
            try {
                return createSession(dataHandler, serverUrl);
            } catch (IOException | DeploymentException e) {
                throw new RuntimeException("获取session异常", e);
            }
        };
    }

}
