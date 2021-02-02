package com.snail.client.accept;

import com.snail.core.handler.DataHandler;
import com.snail.core.handler.WpSelectCommonHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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

    public WpCommonAccept(byte[] addr, int port, WpSelectCommonHandler.SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this(InetAddress.getByAddress(addr), port, selectionKeyConsumer);
    }

    public WpCommonAccept(InetAddress bindAddr, int port, WpSelectCommonHandler.SelectionKeyConsumer selectionKeyConsumer) throws IOException {
        this.bindAddr = bindAddr;
        this.port = port;
        wpSelectCommonHandler = new WpSelectCommonHandler(selectionKeyConsumer);
        startBind();
    }

    private void startBind() {
        ServerSocketChannel socketChannel;
        try {
            socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            InetSocketAddress inetSocketAddress = new InetSocketAddress(bindAddr, port);
            socketChannel.bind(inetSocketAddress);
            wpSelectCommonHandler.registerChannel(socketChannel, SelectionKey.OP_ACCEPT, this, null);
            log.trace("绑定成功 {}:{}", inetSocketAddress.getHostString(), inetSocketAddress.getPort());
        } catch (IOException e) {
            throw new RuntimeException("绑定端口失败", e);
        }
    }

}
