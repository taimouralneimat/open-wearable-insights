import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.cyclonedx)
}

group = "com.openwearableinsights"
version = "0.1.0-SNAPSHOT"

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

    // Spring AI (Ollama, loopback only)
    implementation(libs.spring.ai.ollama.spring.boot.starter)

    // OpenAPI 3
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // PostgreSQL driver
    runtimeOnly(libs.postgresql)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
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