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
@ConfigurationProperties("wp.socket5.client")
public class WpSocket5ClientProperties {

    /**
     * 本地绑定地址
     */
    private String bindAddr;

    /**
     * 本地绑定端口
     */
    private Integer bindPort;

    /**
     * 服务端地址
     */
    private String serverUrl;

}
