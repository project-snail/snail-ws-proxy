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

    static {
        try {
            WpServerEndpoint.dataHandler = new DataHandler();
        } catch (IOException e) {
            throw new RuntimeException("初始化wp处理失败", e);
        }
    }

    @OnMessage
    public void onMessage(ByteBuffer message, Session session) {
        dataHandler.handleSessionMsg(session, message);
    }

    @OnClose
    public void OnClose(Session session) {
        dataHandler.closeSession(session);
    }

}
