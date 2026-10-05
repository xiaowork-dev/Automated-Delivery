package com.xiaowork.autodelivery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.xiaowork.autodelivery.mapper")
@EnableScheduling
public class DeliveryApplication {
    public static void main(String[] args) { SpringApplication.run(DeliveryApplication.class, args); }
}
