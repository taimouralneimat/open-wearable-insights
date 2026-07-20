package com.openwearableinsights.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith verification test.
 *
 * <p>Verifies module boundaries are respected. Cross-module access only via
 * explicitly exposed application services / ports.
 */
class ModuleVerificationTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules.of(OpenWearableInsightsApplication.class).verify();
    }

    @Test
    void documentModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(OpenWearableInsightsApplication.class);
        new Documenter(modules).writeDocumentation();
    }
}