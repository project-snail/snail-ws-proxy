package com.snail.client.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.List;

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
@ConfigurationProperties("wp.port.client")
public class WpPortClientProperties {
    /**
     * 服务端地址
     */
    private String serverUrl;

    private List<PortForwarding> portForwardingList;

    @Data
    public static class PortForwarding {
        /**
         * 远程地址
         */
        private String remoteAddress;

        /**
         * 远程端口
         */
        private Integer remotePort;

        /**
         * 本地绑定地址
         */
        private String bindAddr;

        /**
         * 本地绑定端口
         */
        private Integer bindPort;
    }


}
