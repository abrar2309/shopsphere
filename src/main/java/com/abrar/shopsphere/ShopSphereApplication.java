package com.abrar.shopsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShopSphereApplication {

    public static void main(String[] args) {

        SpringApplication.run(
                ShopSphereApplication.class,
                args
        );
    }
}