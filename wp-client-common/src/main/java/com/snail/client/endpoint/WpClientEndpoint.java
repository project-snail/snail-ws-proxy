package com.snail.client.endpoint;

import com.snail.core.handler.DataHandler;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.nio.ByteBuffer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client.endpoint
 * @Description:
 * @date: 2021/02/01
 */
@Slf4j
@ClientEndpoint
public class WpClientEndpoint {

    private final DataHandler dataHandler;

    public WpClientEndpoint(DataHandler dataHandler) {
        this.dataHandler = dataHandler;
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
