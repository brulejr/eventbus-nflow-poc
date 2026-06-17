import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.jrb.labs"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}


configurations.configureEach {
    // nFlow pulls in older optional support libraries that conflict with the Spring Boot 3.5 stack.
    // H2 itself already contains the MVStore classes; keeping the old h2-mvstore jar on the
    // runtime classpath causes NoSuchMethodError with Spring Boot's managed H2 version.
    exclude(group = "com.h2database", module = "h2-mvstore")

    // Keep Spring Boot's Logback provider as the only SLF4J binding.
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "ch.qos.reload4j", module = "reload4j")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    // nFlow is intentionally isolated behind workflow.engine.WorkflowEngineAdapter.
    // See workflow/nflow for first-pass definitions and the README notes before hard-wiring it.
    implementation("io.nflow:nflow-engine:11.0.0")
    implementation("io.nflow:nflow-rest-api-spring-web:11.0.0")

    // Embedded local broker. Standalone profile starts Moquette in-process.
    implementation("io.moquette:moquette-broker:0.17")

    // Direct RabbitMQ client; no Spring AMQP autoconfiguration in local development.
    implementation("com.rabbitmq:amqp-client:5.25.0")

    // Direct MQTT client; no Spring Integration. This is the same family of client you can replace
    // with your ksb-commons HiveMQ wrapper once this POC moves into your library ecosystem.
    implementation("com.hivemq:hivemq-mqtt-client:1.3.5")

    runtimeOnly("com.h2database:h2:2.3.232")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
