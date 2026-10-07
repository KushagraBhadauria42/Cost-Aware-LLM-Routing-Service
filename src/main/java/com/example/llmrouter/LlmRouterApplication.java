package com.example.llmrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmRouterApplication.class, args);
    }
}
