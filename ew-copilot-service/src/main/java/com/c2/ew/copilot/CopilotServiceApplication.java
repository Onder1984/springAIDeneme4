package com.c2.ew.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

@SpringBootApplication
public class CopilotServiceApplication {

    public static void main(String[] args) {
        // Turkish Locale Gotcha Fix: Prevents "string" -> "STRİNG" in Gemini Tool Schemas
        Locale.setDefault(Locale.US);
        SpringApplication.run(CopilotServiceApplication.class, args);
    }
}
