import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.voicebot"
version = "1.0.0"

application {
    mainClass.set("ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")
    implementation("io.ktor:ktor-server-forwarded-header:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    
    // Ktor Client
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    
    // Telegram Bot API
    implementation("com.github.pengrad:java-telegram-bot-api:7.9.1")
    
    // JSON Processing
    implementation("com.google.code.gson:gson:2.11.0")
    
    // Database
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    
    // Date/Time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveVersion.set("")
        archiveClassifier.set("")
        mergeServiceFiles()
        manifest { attributes["Main-Class"] = application.mainClass.get() }
    }
    jar {
        enabled = false
    }
    build { dependsOn(shadowJar) }
}