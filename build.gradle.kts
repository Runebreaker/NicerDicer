plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
}

group = "de.nicerdicer"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://snapshots.kord.dev")
}

dependencies {
    implementation("dev.kord:kord-core:0.18.1")
    implementation("dev.kord.x:emoji:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("io.ktor:ktor-client-cio:3.5.1")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("de.nicerdicer.Main")
}