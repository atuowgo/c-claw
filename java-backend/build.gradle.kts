plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "cc.claw"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.anthropic:anthropic-java:2.34.1")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Copy bootJar output to target/ for Electron dev-mode auto-start
tasks.register<Copy>("copyJarToTarget") {
    dependsOn(tasks.bootJar, tasks.jar)
    from(layout.buildDirectory.dir("libs"))
    into(layout.projectDirectory.dir("target"))
    include("*.jar")
}

tasks.named("build") {
    dependsOn("copyJarToTarget")
}