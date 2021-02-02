package com.snail.core.holder;

import com.snail.core.handler.DataHandler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.core.holder
 * @Description:
 * @date: 2021/02/01
 */
@Slf4j
@Data
public class SessionHolder {

    @Setter(AccessLevel.PROTECTED)
    private Session session;

    private final LinkInfo linkInfo;

    public SessionHolder(Session session, String remoteAddr, int remotePort) {
        this.session = session;
        this.linkInfo = new LinkInfo(remoteAddr, remotePort);
    }

    public SessionHolder(Session session, DataHandler dataHandler, SocketChannel socketChannel) throws IOException {
        this.session = session;
        this.linkInfo = new LinkInfo(socketChannel);
        dataHandler.registerChannel(
            socketChannel,
            SelectionKey.OP_READ,
            new WeakReference<>(this),
            linkInfo::setSelectionKey
        );
    }

    public SocketChannel activeRemoteStream(DataHandler dataHandler) throws IOException {
        if (linkInfo.isActive()) {
            return linkInfo.getSocketChannel();
        }
        synchronized (this) {
            if (linkInfo.isActive()) {
                return linkInfo.getSocketChannel();
            }
            SocketChannel socketChannel = SocketChannel.open(
                new InetSocketAddress(
                    linkInfo.getInetAddress().getHostAddress(),
                    linkInfo.getRemotePort()
                )
            );
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE)
                .setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
            dataHandler.registerChannel(
                socketChannel,
                SelectionKey.OP_READ,
                new WeakReference<>(this),
                selectionKey -> {
                    linkInfo.setSelectionKey(selectionKey);
                    log.trace("建立连接 --> {}", linkInfo);
                }
            );
            linkInfo.setSocketChannel(socketChannel);
            linkInfo.setActive(true);
            return socketChannel;
        }
    }

    public SocketChannel getSocketChannel() {
        return linkInfo.getSocketChannel();
    }

    @Data
    public class LinkInfo {

        private final String remoteAddr;

        private final int remotePort;

        private boolean isActive = false;

        private SocketChannel socketChannel;

        private SelectionKey selectionKey;

        private InetAddress inetAddress;

        LinkInfo(String remoteAddr, int remotePort) {
            this.remoteAddr = remoteAddr;
            this.remotePort = remotePort;
            try {
                inetAddress = InetAddress.getByName(remoteAddr);
            } catch (UnknownHostException e) {
                throw new RuntimeException("解析远程地址异常", e);
            }
        }

        LinkInfo(SocketChannel socketChannel) {
            Socket socket = socketChannel.socket();
            inetAddress = socket.getInetAddress();
            this.socketChannel = socketChannel;
            this.remoteAddr = inetAddress.getHostAddress();
            this.remotePort = socket.getPort();
            this.isActive = true;
        }

        @Override
        public String toString() {
            return String.format("%s:%d", remoteAddr, remotePort);
        }
    }

}
