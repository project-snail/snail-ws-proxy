package com.snail.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @version V1.0
 * @author: csz
 * @Title
 * @Package: com.snail.client
 * @Description:
 * @date: 2021/03/26
 */
@SpringBootApplication
public class WpTerminalClientApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(WpTerminalClientApplication.class, args);
    }
}
