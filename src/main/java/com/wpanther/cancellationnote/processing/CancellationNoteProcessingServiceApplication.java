package com.wpanther.cancellationnote.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = "com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence")
@EnableDiscoveryClient
@EnableScheduling
public class CancellationNoteProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CancellationNoteProcessingServiceApplication.class, args);
    }
}
