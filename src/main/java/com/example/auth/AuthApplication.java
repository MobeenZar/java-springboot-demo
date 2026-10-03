package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the stateless JWT login API.
 * <p>
 * {@link ConfigurationPropertiesScan} registers the {@code @ConfigurationProperties} records (e.g.
 * {@code JwtProperties}) without listing each one. The app runs against an existing Oracle schema and never
 * creates or migrates tables.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
