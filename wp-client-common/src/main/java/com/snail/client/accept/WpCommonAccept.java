package com.snail.client.accept;

import com.snail.core.handler.WpSelectCommonHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ThreadFactory;
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

    private EventLoopGroup bossEventLoopGroup;

    private ChannelHandler channelHandler;

    private EventLoopGroup workerEventLoopGroup;

    private ChannelFuture future;

    public WpCommonAccept(byte[] addr, int port, ChannelHandler channelHandler) throws IOException {
        this(InetAddress.getByAddress(addr), port, null, channelHandler, null);
    }

    public WpCommonAccept(InetAddress bindAddr, int port, ChannelHandler channelHandler) throws IOException {
        this(bindAddr, port, null, channelHandler, null);
    }

    public WpCommonAccept(byte[] addr, int port, WpSelectCommonHandler.MsgConsumer msgConsumer) throws IOException {
        this(InetAddress.getByAddress(addr), port, null, null, msgConsumer);
    }

    public WpCommonAccept(InetAddress bindAddr, int port, Consumer<Channel> acceptChannelConsumer, WpSelectCommonHandler.MsgConsumer msgConsumer) throws IOException {
        this(bindAddr, port, acceptChannelConsumer, null, msgConsumer);
    }

    public WpCommonAccept(byte[] addr, int port, Consumer<Channel> acceptChannelConsumer, ChannelHandler channelHandler, WpSelectCommonHandler.MsgConsumer msgConsumer) throws IOException {
        this(InetAddress.getByAddress(addr), port, acceptChannelConsumer, channelHandler, msgConsumer);
    }

    public WpCommonAccept(InetAddress bindAddr, int port, Consumer<Channel> acceptChannelConsumer, ChannelHandler channelHandler, WpSelectCommonHandler.MsgConsumer msgConsumer) throws IOException {
        this.bindAddr = bindAddr;
        this.port = port;
        if (channelHandler == null) {
            channelHandler = new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(
                            new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                public void channelRegistered(ChannelHandlerContext ctx) {
                                    if (acceptChannelConsumer != null) {
                                        acceptChannelConsumer.accept(ctx.channel());
                                    }
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                    if (msgConsumer != null) {
                                        try {
                                            msgConsumer.accept(ctx, msg);
                                        } catch (IOException e) {
                                            msgConsumer.afterIOEx(ctx);
                                        }
                                    }
                                }
                            }
                        );
                }
            };
        }
        this.channelHandler = channelHandler;
    }

    public void startBind() {

        initGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(bossEventLoopGroup, workerEventLoopGroup)
            .channel(chooseChannel())
            .option(ChannelOption.SO_BACKLOG, 1024)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .childHandler(this.channelHandler);

        try {
            future = bootstrap.bind(bindAddr, port).sync();
            future.channel().closeFuture().addListener(
                (ChannelFutureListener) future -> {
                    bossEventLoopGroup.shutdownGracefully();
                    workerEventLoopGroup.shutdownGracefully();
                }
            );
        } catch (InterruptedException e) {
            log.error("绑定异常", e);
        }

    }

    private void initGroup() {

        int corePoolSize = Runtime.getRuntime().availableProcessors();

        if (Epoll.isAvailable()) {
            bossEventLoopGroup = new EpollEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            workerEventLoopGroup = new EpollEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory( "WpEventLoopGroupWorker")
            );
        } else if (KQueue.isAvailable()) {
            bossEventLoopGroup = new KQueueEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            workerEventLoopGroup = new KQueueEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
        } else {
            bossEventLoopGroup = new NioEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            workerEventLoopGroup = new NioEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
        }
    }

    private Class<? extends ServerSocketChannel> chooseChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }

    public void close() {
        if (future != null && future.channel().isOpen()) {
            try {
                future.channel().close().sync();
            } catch (InterruptedException ignored) {
            }
        }
    }

}
