package com.snail.core.handler;

import javax.websocket.Session;
import java.nio.ByteBuffer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.core.handler
 * @Description:
 * @date: 2021/02/03
 */
public interface WpSessionMsgExtHandle {

    void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler);

    /**
     * 返回需要处理的类型
     *
     * 注意: 0和1 已经使用
     * @return 返回需要处理的类型
     */
    byte handleType();

}
