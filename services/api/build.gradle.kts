import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.cyclonedx)
}

group = "com.openwearableinsights"
version = "0.1.0-SNAPSHOT"

// Override the embedded Tomcat version the Spring Boot 3.5.16 BOM still
// manages (10.1.55) — that version carries three real, fixed CRITICAL
// CVEs (2026-65182, 2026-65905, 2026-68525; access-control/auth-bypass).
// Found via a Trivy scan of this project's own CycloneDX SBOM. 10.1.58 (the
// CVEs' cited fixed version) was never published to Maven Central; 10.1.59
// is the latest available and supersedes it.
extra["tomcat.version"] = "10.1.59"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)

    // Spring Modulith
    implementation(libs.spring.modulith.starter.core)

    // Note: no Spring AI dependency. LlmInsightService talks to Ollama's
    // REST API directly via plain Spring Web RestClient — see that class's
    // Javadoc for why (Spring AI 1.0.1's OllamaOptions can't express the
    // "think": false request field this app's default model needs).

    // OpenAPI 3
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Garmin FIT SDK (ADR-0006) — official artifact, published to Maven Central
    implementation(libs.garmin.fit.sdk)

    // PostgreSQL driver
    runtimeOnly(libs.postgresql)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Generate SBOM (CycloneDX)
tasks.cyclonedxBom {
    setOutputFormat("json")
}

// Allow --load-synthetic arg for the synthetic data loader
tasks.withType<BootRun> {
    if (project.hasProperty("--load-synthetic")) {
        args("--load-synthetic")
    }
}