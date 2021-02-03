package com.snail.client.accept;

import com.snail.client.properties.WpSocket5ClientProperties;
import com.snail.core.handler.DataHandler;
import com.snail.client.util.WpSessionUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

    private WpSocket5ClientProperties wpSocket5ClientProperties;

    private DataHandler dataHandler;

    private WpCommonAccept wpCommonAccept;

    public LinkAcceptHandler(WpSocket5ClientProperties wpSocket5ClientProperties) throws IOException {
        this.wpSocket5ClientProperties = wpSocket5ClientProperties;
        dataHandler = new DataHandler();
        createSessionFun = WpSessionUtil.createSessionFun(dataHandler, wpSocket5ClientProperties.getServerUrl());
        wpCommonAccept = new WpCommonAccept(
            InetAddress.getByName(wpSocket5ClientProperties.getBindAddr()),
            wpSocket5ClientProperties.getBindPort(),
            this::handlerSelect
        );
        wpCommonAccept.startBind();
    }

    private void handlerSelect(SelectionKey selectionKey) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();

        SocketChannel socketChannel;
        socketChannel = serverSocketChannel.accept();
        SocketLinkInfo socketLinkInfo = handlerSocketLink(socketChannel);
        Session session = createSessionFun.get();
//             绑定远程连接
        dataHandler.writeRemoteInfoToSessionAndRegister(
            session,
            socketLinkInfo.getAddr(),
            socketLinkInfo.getPort(),
            socketChannel
        );

    }

    private SocketLinkInfo handlerSocketLink(SocketChannel socketChannel) throws IOException {
        ByteBuffer data = readSpecifiedByteSize(socketChannel, 1);

        if (data.get() != 0x05) {
            socketChannel.close();
            log.warn("不支持的链接协议");
            throw new UnsupportedOperationException("不支持的链接协议");
        }

        data = readSpecifiedByteSize(socketChannel, 1);

        data = readSpecifiedByteSize(socketChannel, data.get());

        if (data.get() != 0x00) {
            socketChannel.close();
            log.warn("不支持的认证方式");
            throw new UnsupportedOperationException("不支持的链接协议");
        }
        socketChannel.write(ByteBuffer.wrap(new byte[]{0x05, 0x00}));

        data = readSpecifiedByteSize(socketChannel, 4);

        byte ATYP = data.get(3);

        byte[] addr;
        String host;

        ByteBuffer res = ByteBuffer.allocate(1024);
        res.put(new byte[]{0x05, 0x00, 0x00, ATYP});

        switch (ATYP) {
            case 0x01:
                data = readSpecifiedByteSize(socketChannel, 4);
                addr = data.array();
                InetAddress inetAddress = InetAddress.getByAddress(addr);
                host = inetAddress.getHostAddress();
                res.put(addr);
                break;
            case 0x03:
                data = readSpecifiedByteSize(socketChannel, 1);
                data = readSpecifiedByteSize(socketChannel, data.get());
                host = new String(data.array(), StandardCharsets.UTF_8);
                res.put(data.array());
                break;
            case 0x06:
                throw new RuntimeException("不支持ipv6");
            default:
                throw new RuntimeException("位置链接类型");
        }

        data = readSpecifiedByteSize(socketChannel, 2);

        res.put(data.array());

        short port = data.getShort();

        res.flip();
        socketChannel.write(res);

        return new SocketLinkInfo(host, port);
    }

    private ByteBuffer readSpecifiedByteSize(SocketChannel socketChannel, int size) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        while (byteBuffer.position() != size) {
            socketChannel.read(byteBuffer);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    @Data
    @AllArgsConstructor
    private class SocketLinkInfo {
        private String addr;
        private int port;
    }


}
