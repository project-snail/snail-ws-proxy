package com.snail.client.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client.properties
 * @Description:
 * @date: 2021/02/01
 */
@Data
@Component
@ConfigurationProperties("wp.pass-proxy.client")
public class WpPassProxyClientProperties {

    /**
     * 本地链接地址
     */
    private String localAddr;

    /**
     * 本地链接端口
     */
    private Integer localPort;

    /**
     * 本地绑定地址
     */
    private String remoteBindAddr;

    /**
     * 本地绑定端口
     */
    private int remoteBindPort;

    /**
     * 服务端地址
     */
    private String serverUrl;

}
