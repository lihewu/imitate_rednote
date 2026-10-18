package com.rednote.rednoteauth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.rednote.rednoteauth.domain.mapper")
public class RednoteAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(RednoteAuthApplication.class, args);
    }

}
