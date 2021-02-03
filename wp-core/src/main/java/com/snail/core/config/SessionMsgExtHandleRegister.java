package com.snail.core.config;

import com.snail.core.handler.DataHandler;
import com.snail.core.handler.WpSessionMsgExtHandle;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.config
 * @Description: 注册
 * @date: 2021/02/03
 */
public class SessionMsgExtHandleRegister {

    /**
     * 注册额外的消息处理器
     *
     * @param wpSessionMsgExtHandle 消息处理器实例
     */
    public void registerSessionMsgExtHandle(WpSessionMsgExtHandle wpSessionMsgExtHandle) {
        DataHandler.registerSessionMsgExtHandle(wpSessionMsgExtHandle);
    }

}
