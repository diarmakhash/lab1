package org.narxoz.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.narxoz")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}