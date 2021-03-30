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
@ConfigurationProperties("wp.terminal.client")
public class WpTerminalClientProperties {

    /**
     * 服务端地址
     */
    private String serverUrl;


}
