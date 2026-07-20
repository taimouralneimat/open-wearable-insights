package com.openwearableinsights.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Open Wearable Insights — Spring Boot modular monolith.
 *
 * <p>Local-first, privacy-first wearable analytics. Binds to loopback only.
 * Works fully without an LLM via a deterministic fallback engine.
 */
@SpringBootApplication
public class OpenWearableInsightsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenWearableInsightsApplication.class, args);
    }
}