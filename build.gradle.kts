import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.ethereum"
version = "0.0.1-SNAPSHOT"
description = "Discovery v5 research and simulations"

plugins {
    java
    idea
    kotlin("jvm") version "1.3.31"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.libp2p:jvm-libp2p-minimal:0.3.2-RELEASE")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
