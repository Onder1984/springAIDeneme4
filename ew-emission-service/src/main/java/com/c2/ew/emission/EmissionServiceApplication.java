package com.c2.ew.emission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmissionServiceApplication.class, args);
    }
}
