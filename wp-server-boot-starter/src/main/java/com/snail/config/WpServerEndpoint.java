package com.snail.config;

import com.snail.core.handler.DataHandler;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.server.endpoint
 * @Description:
 * @date: 2021/02/01
 */
@Slf4j
public class WpServerEndpoint {

    private static DataHandler dataHandler;

    @OnMessage
    public void onMessage(ByteBuffer message, Session session) {
        dataHandler.handleSessionMsg(session, message);
    }

    @OnClose
    public void OnClose(Session session) {
        dataHandler.closeSession(session);
    }

    public static synchronized void init() {
        if (WpServerEndpoint.dataHandler != null) {
            return;
        }
        WpServerEndpoint.dataHandler = new DataHandler();
    }

    public static synchronized void init(int corePoolSize) {
        if (WpServerEndpoint.dataHandler != null) {
            return;
        }
        WpServerEndpoint.dataHandler = new DataHandler(corePoolSize);
    }

}
