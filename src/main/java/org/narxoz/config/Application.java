package org.narxoz.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "org.narxoz")
@EntityScan("org.narxoz.persistence") // Application lives in config; the entity lives in persistence
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
