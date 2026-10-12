package com.axioma.loyalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Flyway se configura a mano en DatabaseConfig (usa la misma cadena que R2DBC).
@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
public class LoyaltyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoyaltyServiceApplication.class, args);
    }
}
