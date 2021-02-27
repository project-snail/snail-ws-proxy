package com.snail.client.accept;

import com.snail.client.properties.WpSocket5ClientProperties;
import com.snail.core.handler.DataHandler;
import com.snail.client.util.WpSessionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.io.IOException;
import java.net.InetAddress;
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
            new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(
                            Socks5ServerEncoder.DEFAULT,
                            new Socks5InitialRequestDecoder(),
                            new Socks5CommandRequestDecoder(),
                            new SimpleChannelInboundHandler<DefaultSocks5InitialRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) {
                                    ctx.channel().config().setAutoRead(false);
                                    if (!msg.version().equals(SocksVersion.SOCKS5) || msg.decoderResult().isFailure()) {
                                        log.warn("异常协议连接");
                                        ctx.close();
                                        return;
                                    }
                                    Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(
                                        Socks5AuthMethod.NO_AUTH
                                    );
                                    ctx.writeAndFlush(initialResponse);
                                    ctx.channel().read();
                                }
                            },
                            new SimpleChannelInboundHandler<DefaultSocks5CommandRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
                                    handlerMsg(ctx, msg);
                                    ctx.channel().read();
                                }
                            }
                        );
                }
            }
        );
        wpCommonAccept.startBind();
    }

    private void handlerMsg(ChannelHandlerContext ctx, Socks5CommandRequest socks5CommandRequest) {

        if (Socks5CommandType.CONNECT.equals(socks5CommandRequest.type())) {

            Channel channel = ctx.channel();

            Session session = createSessionFun.get();

            channel.pipeline().addFirst(
                    new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            dataHandler.doHandle(ctx, msg);
                        }
                    }
                );

            //             绑定远程连接
            dataHandler.writeRemoteInfoToSessionAndRegister(
                session,
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort(),
                channel
            );

            Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                socks5CommandRequest.dstAddrType(),
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort()
            );

            channel.writeAndFlush(commandResponse);

            return;

        }

        ctx.fireChannelRead(socks5CommandRequest);

    }


}
