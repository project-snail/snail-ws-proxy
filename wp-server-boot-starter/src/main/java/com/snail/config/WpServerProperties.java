package com.snail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.config
 * @Description:
 * @date: 2021/02/02
 */
@Data
@ConfigurationProperties(prefix = "wp.server")
public class WpServerProperties {

    private String endpointPath;

}
