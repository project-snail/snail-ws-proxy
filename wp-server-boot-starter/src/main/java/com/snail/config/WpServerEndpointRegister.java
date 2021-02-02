package com.snail.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.WebApplicationObjectSupport;

import javax.servlet.ServletContext;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.config
 * @Description:
 * @date: 2021/02/02
 */
public class WpServerEndpointRegister extends WebApplicationObjectSupport implements InitializingBean {

    @Autowired
    private WpServerProperties wpServerProperties;

    private ServerContainer serverContainer;

    @Override
    protected void initServletContext(ServletContext servletContext) {
        super.initServletContext(servletContext);
        this.serverContainer =
            (ServerContainer) servletContext.getAttribute("javax.websocket.server.ServerContainer");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        serverContainer.addEndpoint(
            ServerEndpointConfig.Builder.create(
                WpServerEndpoint.class,
                wpServerProperties.getEndpointPath()
            ).build()
        );
    }
}
