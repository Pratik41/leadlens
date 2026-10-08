package com.leadlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LeadLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadLensApplication.class, args);
    }
}
