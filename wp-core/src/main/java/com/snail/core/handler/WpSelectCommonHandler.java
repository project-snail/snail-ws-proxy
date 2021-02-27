package com.snail.core.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.server.accept
 * @Description:
 * @date: 2021/02/02
 */
@Slf4j
public class WpSelectCommonHandler {

    private final EventLoopGroup eventLoopGroupWorker;

    private final int corePoolSize;

    private final MsgConsumer msgConsumer;

    private Bootstrap bootstrap;

    public WpSelectCommonHandler(int corePoolSize, MsgConsumer msgConsumer) {
        this.corePoolSize = corePoolSize;
        this.msgConsumer = msgConsumer;

        Class<? extends Channel> channelClass;
        if (Epoll.isAvailable()) {
            eventLoopGroupWorker = new EpollEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            channelClass = EpollSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            eventLoopGroupWorker = new KQueueEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            channelClass = KQueueSocketChannel.class;
        } else {
            eventLoopGroupWorker = new NioEventLoopGroup(
                corePoolSize,
                new DefaultThreadFactory("WpEventLoopGroupWorker")
            );
            channelClass = NioSocketChannel.class;
        }

        bootstrap = new Bootstrap().group(eventLoopGroupWorker)
            .channel(channelClass)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.AUTO_READ, false)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .handler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            .addLast(
                                new DefaultEventExecutorGroup(
                                    corePoolSize, new DefaultThreadFactory("WpEventExecutorGroup")
                                ),
                                new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                        try {
                                            msgConsumer.accept(ctx, msg);
                                        } catch (IOException e) {
                                            msgConsumer.afterIOEx(ctx);
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        super.channelInactive(ctx);
                                        ctx.close();
                                        msgConsumer.channelInactive(ctx);
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        log.debug("通道异常", cause);
                                        ctx.close();
                                    }
                                }
                            );
                    }
                }
            );
    }

    public ChannelFuture open(InetSocketAddress inetSocketAddress) {
        return bootstrap.connect(inetSocketAddress.getAddress(), inetSocketAddress.getPort());
    }

    @FunctionalInterface
    public interface MsgConsumer {

        void accept(ChannelHandlerContext context, ByteBuf msg) throws IOException;

        default void afterIOEx(ChannelHandlerContext context) {
        }

        default void channelInactive(ChannelHandlerContext context) {
        }

        static MsgConsumer build(MsgConsumer msgConsumer, Consumer<ChannelHandlerContext> afterIOExFun, Consumer<ChannelHandlerContext> channelInactiveFun) {
            return new MsgConsumer() {
                @Override
                public void accept(ChannelHandlerContext context, ByteBuf msg) throws IOException {
                    msgConsumer.accept(context, msg);
                }

                @Override
                public void afterIOEx(ChannelHandlerContext context) {
                    afterIOExFun.accept(context);
                }

                @Override
                public void channelInactive(ChannelHandlerContext context) {
                    channelInactiveFun.accept(context);
                }
            };
        }
    }
}
