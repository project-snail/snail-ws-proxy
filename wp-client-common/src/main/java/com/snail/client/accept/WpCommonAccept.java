package com.snail.client.accept;

import com.snail.core.handler.WpSelectCommonHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.*;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client.accept
 * @Description:
 * @date: 2021/02/02
 */
@Slf4j
public class WpCommonAccept {

    private final InetAddress bindAddr;

    private final int port;

    private WpSelectCommonHandler wpSelectCommonHandler;

    private SelectionKey selectionKey;

    public WpCommonAccept(byte[] addr, int port, WpSelectCommonHandler.SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this(InetAddress.getByAddress(addr), port, selectionKeyConsumer);
    }

    public WpCommonAccept(InetAddress bindAddr, int port, WpSelectCommonHandler.SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this.bindAddr = bindAddr;
        this.port = port;
        wpSelectCommonHandler = new WpSelectCommonHandler(selectionKeyConsumer);
    }

    public void startBind() {
        ServerSocketChannel socketChannel;
        try {
            socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            InetSocketAddress inetSocketAddress = new InetSocketAddress(bindAddr, port);
            socketChannel.bind(inetSocketAddress);
            wpSelectCommonHandler.registerChannel(socketChannel, SelectionKey.OP_ACCEPT, this, this::setSelectionKey);
            log.trace("绑定成功 {}:{}", inetSocketAddress.getHostString(), inetSocketAddress.getPort());
        } catch (IOException e) {
            throw new RuntimeException("绑定端口失败", e);
        }
    }

    public void close() {
        if (this.selectionKey == null) {
            return;
        }
        selectionKey.cancel();
        try {
            selectionKey.channel().close();
        } catch (IOException e) {
            log.warn("关闭channel异常", e);
        }
    }

    private void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }
}
