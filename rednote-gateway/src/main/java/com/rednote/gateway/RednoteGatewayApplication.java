package com.rednote.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Rednote 网关启动入口。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class RednoteGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RednoteGatewayApplication.class, args);
    }
}
