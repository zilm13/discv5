import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.ethereum"
version = "0.0.1-SNAPSHOT"
description = "Discovery v5 research and simulations"

plugins {
    java
    idea
    kotlin("jvm") version "1.3.71"
}

repositories {
    jcenter()
    mavenCentral()
}

val log4j2Version = "2.11.2"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.libp2p:jvm-libp2p-minimal:0.3.2-RELEASE")
    implementation ("org.apache.tuweni:tuweni-bytes:1.1.0")
    implementation("com.google.guava:guava:27.1-jre")
    implementation("org.apache.logging.log4j:log4j-api:${log4j2Version}")
    implementation("org.apache.logging.log4j:log4j-core:${log4j2Version}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}
