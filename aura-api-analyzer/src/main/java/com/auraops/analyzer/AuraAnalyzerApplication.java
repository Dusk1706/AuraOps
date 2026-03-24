package com.auraops.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AuraAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuraAnalyzerApplication.class, args);
    }
}
