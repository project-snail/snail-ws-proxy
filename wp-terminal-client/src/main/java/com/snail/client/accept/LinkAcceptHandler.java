package com.snail.client.accept;

import com.jediterm.pty.PtyMain;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.snail.client.properties.WpTerminalClientProperties;
import com.snail.client.util.WpSessionUtil;
import com.snail.core.handler.DataHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.LockSupport;
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

    private DataHandler dataHandler;

    public LinkAcceptHandler(WpTerminalClientProperties properties) {
        dataHandler = new DataHandler();
        createSessionFun = WpSessionUtil.createSessionFun(dataHandler, properties.getServerUrl());
        new RemotePtyMain();

    }

    class LinkedByteArrayInputStream extends ByteArrayInputStream {

        private BlockingDeque<byte[]> dataList = new LinkedBlockingDeque<>();

        private volatile boolean isDone = false;

        private Deque<Thread> parkThreadList = new ConcurrentLinkedDeque<>();

        public LinkedByteArrayInputStream() {
            super(new byte[0]);
        }

        /**
         * 添加数据到队列中 同时会唤醒正在等待数据的线程
         * @param bytes 添加到队列的数据
         */
        public void add(byte[] bytes) {
            dataList.addLast(bytes);
//            唤醒正在等待数据的线程
            if (!parkThreadList.isEmpty()) {
                Iterator<Thread> iterator = parkThreadList.iterator();
                while (iterator.hasNext()) {
                    Thread thread = iterator.next();
                    LockSupport.unpark(thread);
                    iterator.remove();
                }
            }
        }

        @Override
        public synchronized int read() {
            int read = super.read();
            if (read == -1 && poll()) {
                return read();
            }
            if (read == -1 && !isDone) {
                parkThreadList.addLast(Thread.currentThread());
                LockSupport.park();
                if (!isDone) {
                    return read();
                }
            }
            return read;
        }

        /**
         * 重写读取的方法，会先从buf读取 如果读取完了，回去尝试从队列poll。
         * 如果poll失败且未完成，会进入阻塞状态
         */
        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int read = super.read(b, off, len);
            if (read == -1 && poll()) {
                return read(b, off, len);
            }
            if (read == -1 && !isDone) {
                parkThreadList.addLast(Thread.currentThread());
                LockSupport.park();
                if (!isDone) {
                    return read(b, off, len);
                }
            }
            return read;
        }

        private boolean poll() {
            if (dataList.isEmpty()) {
                return false;
            }
            this.buf = dataList.poll();
            this.pos = 0;
            this.count = buf.length;
            return true;
        }

    }

    class RemotePtyProcess extends PtyProcess {

        private LinkedByteArrayInputStream inputStream = new LinkedByteArrayInputStream();

        private final Session session;

        private WinSize winSize;

        public RemotePtyProcess(Session session) {
            this.session = session;
//            注册session数据消费者 添加到管道队列中
            dataHandler.registerSession(
                session,
                dataBuffer -> inputStream.add(dataBuffer.array())
            );
//            注册session关闭事件 设置完成标识和唤醒等待队列
            dataHandler.registerCloseSessionConsumer(
                session,
                closeSession -> {
                    inputStream.isDone = true;
                    inputStream.add(new byte[0]);
                }
            );
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void setWinSize(WinSize winSize) {
            this.winSize = winSize;
            ByteBuffer buffer = ByteBuffer.allocate(1 + 8);
            buffer.put((byte) 12);
            buffer.putShort(winSize.ws_col);
            buffer.putShort(winSize.ws_row);
            buffer.putShort(winSize.ws_xpixel);
            buffer.putShort(winSize.ws_ypixel);
            buffer.flip();
            dataHandler.doWriteRawToSession(
                session,
                buffer,
                res -> {
                    if (!res.isOK()) {
                        log.error("写入远端session失败", res.getException());
                    }
                }
            );
        }

        @Override
        public WinSize getWinSize() {
            return winSize;
        }

        @Override
        public int getPid() {
            return 0;
        }

        @Override
        public OutputStream getOutputStream() {
//            重写写入方法 将写入的数据发送至session端
            return new ByteArrayOutputStream(0) {
                @Override
                public synchronized void write(int b) {
                    dataHandler.doWriteToSession(session, ByteBuffer.wrap(new byte[]{(byte) b}), null);
                }

                @Override
                public synchronized void write(byte[] b, int off, int len) {
                    dataHandler.doWriteToSession(
                        session,
                        ByteBuffer.wrap(b, off, len),
                        res -> {
                            if (!res.isOK()) {
                                log.error("写入远端session失败", res.getException());
                            }
                        }
                    );
                }
            };
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return inputStream;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    class RemotePtyMain extends PtyMain {

        @Override
        public TtyConnector createTtyConnector() {
            Session session = createSessionFun.get();

            ByteBuffer byteBuffer = ByteBuffer.allocate(1);
            byteBuffer.put((byte) 11);
            byteBuffer.flip();
            dataHandler.doWriteRawToSession(session, byteBuffer, null);
            return new PtyProcessTtyConnector(new RemotePtyProcess(session), Charset.forName("UTF-8"));
        }

//        设置一些样式
        protected JediTermWidget createTerminalWidget(TabbedSettingsProvider settingsProvider) {
            JediTermWidget jediTermWidget = new JediTermWidget(new SettingsProvider());
            Terminal terminal = jediTermWidget.getTerminal();
            TextStyle style = new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK);
            terminal.getStyleState().setDefaultStyle(style);
            StyleState styleState = new StyleState();
            styleState.setCurrent(style);
            terminal.getStyleState().set(styleState);
            return jediTermWidget;
        }

    }

    class SettingsProvider extends DefaultTabbedSettingsProvider {

        @Override
        public Font getTerminalFont() {
            return new Font("Menlo", Font.PLAIN, (int) this.getTerminalFontSize());
        }

        @Override
        public float getTerminalFontSize() {
            return 16.0f;
        }
    }


}
