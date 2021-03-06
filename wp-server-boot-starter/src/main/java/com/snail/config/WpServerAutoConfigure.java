package com.snail.config;

import com.snail.core.config.SessionMsgExtHandleRegister;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.config
 * @Description:
 * @date: 2021/02/02
 */
@Configuration
@EnableConfigurationProperties(WpServerProperties.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(value = "wp.server.enable", havingValue = "true")
@Import({
    WpServerEndpointRegister.class,
    SessionMsgExtHandleRegister.class
})
public class WpServerAutoConfigure {
}
