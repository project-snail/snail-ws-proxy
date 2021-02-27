package com.snail.core.holder;

import com.snail.core.handler.DataHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.VoidChannelPromise;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;

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

    public SessionHolder(Session session, DataHandler dataHandler, Channel channel) {
        this.session = session;
        this.linkInfo = new LinkInfo(channel);
        dataHandler.registerChannel(channel, this);
    }

    public ChannelFuture activeRemoteStream(DataHandler dataHandler) {
        if (linkInfo.isActive()) {
            return new VoidChannelPromise(linkInfo.getChannel(), false);
        }
        synchronized (this) {

            if (linkInfo.isActive()) {
                return new VoidChannelPromise(linkInfo.getChannel(), false);
            }

            ChannelFuture channelFuture = dataHandler.open(
                linkInfo.getInetSocketAddress(),
                null
            );

            linkInfo.setChannel(channelFuture.channel());
            dataHandler.registerChannel(channelFuture.channel(), this);
            linkInfo.setState(LinkState.INIT);
            log.trace("建立连接 --> {}", linkInfo);

            return channelFuture;

        }
    }

    public Channel getChannel() {
        return linkInfo.getChannel();
    }

    @Data
    public class LinkInfo {

        private final String remoteAddr;

        private final int remotePort;

        private LinkState state = LinkState.NO_LINK;

        private Channel channel;

        private InetSocketAddress inetSocketAddress;

        private Phaser activePhaser = new Phaser(1);

        LinkInfo(String remoteAddr, int remotePort) {
            this.remoteAddr = remoteAddr;
            this.remotePort = remotePort;
            inetSocketAddress = new InetSocketAddress(remoteAddr, remotePort);
        }

        LinkInfo(Channel channel) {
            inetSocketAddress = (InetSocketAddress) channel.remoteAddress();
            this.channel = channel;
            this.remoteAddr = inetSocketAddress.getHostString();
            this.remotePort = inetSocketAddress.getPort();
            this.state = LinkState.ACTIVE;
        }

        @Override
        public String toString() {
            return String.format("%s:%d", remoteAddr, remotePort);
        }

        public boolean isActive() {
            return LinkState.ACTIVE.equals(this.state);
        }
    }

    public enum LinkState {
        NO_LINK,
        INIT,
        ACTIVE
    }

}
