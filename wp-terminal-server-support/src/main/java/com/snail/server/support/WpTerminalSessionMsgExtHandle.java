package com.snail.server.support;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.snail.core.config.SessionMsgExtHandleRegister;
import com.snail.core.handler.DataHandler;
import com.snail.core.handler.WpSessionMsgExtHandle;
import com.snail.core.type.MsgTypeEnums;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.server.support
 * @Description:
 * @date: 2021/03/26
 */
@Slf4j
@ConditionalOnBean(SessionMsgExtHandleRegister.class)
public class WpTerminalSessionMsgExtHandle {

    private Map<Session, PtyProcess> ptyProcessMap = new ConcurrentHashMap<>();

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private final ExecutorService executorService = Executors.newCachedThreadPool(
        new DefaultThreadFactory("WpTerminalSessionMsgExtHandle")
    );

    @Autowired
    public WpTerminalSessionMsgExtHandle(SessionMsgExtHandleRegister register) {
        register.registerSessionMsgExtHandle(new AcceptSessionMsgExtHandle());
        register.registerSessionMsgExtHandle(new ResizeMsgExtHandle());
    }

    class AcceptSessionMsgExtHandle implements WpSessionMsgExtHandle {

        /**
         * 开启一个本地pty 并且读取pty中的数据至session端
         */
        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {
            PtyProcess ptyProcess = ptyProcessMap.computeIfAbsent(
                session,
                key -> {
                    String[] command;
                    Map<String, String> envs = new HashMap<>();
                    if (IS_WINDOWS) {
                        command = new String[]{"cmd.exe"};
                    } else {
                        command = new String[]{"/bin/bash", "--login"};
                        envs.put("TERM", "xterm");
                    }
                    try {
                        return new PtyProcessBuilder()
                            .setCommand(command)
                            .setEnvironment(envs)
                            .setRedirectErrorStream(true)
                            .start();
                    } catch (IOException e) {
                        throw new RuntimeException("Terminal, 打开Terminal失败", e);
                    }
                }
            );

            //            注册关闭session时的回调
            dataHandler.registerCloseSessionConsumer(
                session,
                WpTerminalSessionMsgExtHandle.this::closeProcess
            );

//            读取数据并发送至远端
            executorService.execute(
                () -> {
                    InputStream inputStream = ptyProcess.getInputStream();
                    byte[] bytes = new byte[1024];
                    int read;
                    try {
                        ByteBuffer buffer;
                        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
                        while ((read = inputStream.read(bytes)) != -1) {
                            if (!session.isOpen()) {
                                return;
                            }
                            buffer = ByteBuffer.allocate(1 + read);
                            buffer.put((byte) MsgTypeEnums.SEND.ordinal());
                            buffer.put(bytes, 0, read);
                            buffer.flip();
                            try {
                                basicRemote.sendBinary(buffer);
                            } catch (Exception e) {
                                log.error("Terminal, 发送给client数据失败", e);
                            }
                        }
                    } catch (IOException e) {
                        log.error("Terminal, 读取Terminal异常", e);
                    }

                }
            );

//            注册数据消费者 并写入至pty中
            OutputStream outputStream = ptyProcess.getOutputStream();
            dataHandler.registerSession(
                session,
                byteBuf -> {
                    try {
                        byte[] data = new byte[byteBuf.remaining()];
                        byteBuf.get(data);
                        outputStream.write(data);
                        outputStream.flush();
                    } catch (IOException e) {
                        log.error("Terminal, 写入Terminal异常", e);
                    }
                }
            );

        }

        @Override
        public byte handleType() {
            return 11;
        }
    }

    class ResizeMsgExtHandle implements WpSessionMsgExtHandle {

        @Override
        public void handleSessionMsg(Session session, ByteBuffer byteBuffer, DataHandler dataHandler) {
            PtyProcess ptyProcess = ptyProcessMap.get(session);
            if (ptyProcess == null) {
                return;
            }
            short[] winSize = new short[4];
            for (int i = 0; i < winSize.length; i++) {
                winSize[i] = byteBuffer.getShort();
            }
            ptyProcess.setWinSize(
                new WinSize(
                    winSize[0],
                    winSize[1],
                    winSize[2],
                    winSize[3]
                )
            );
        }

        @Override
        public byte handleType() {
            return 12;
        }

    }

    private void closeProcess(Session session) {
        PtyProcess ptyProcess = ptyProcessMap.get(session);
        if (ptyProcess != null) {
            ptyProcess.destroy();
        }
    }

}
