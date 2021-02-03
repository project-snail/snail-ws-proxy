package com.snail.server.config;

import com.snail.server.support.WpPassProxySessionMsgExtHandle;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(value = "wp.server.enable", havingValue = "true")
@Import(WpPassProxySessionMsgExtHandle.class)
public class WpPassProxyServerAutoConfigure {
}
