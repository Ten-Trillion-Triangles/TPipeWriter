import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
    id("com.gradleup.shadow") version "9.2.0"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // TPipe library dependencies via composite build
    implementation("com.TTT:TPipe:0.0.1")
    implementation("com.TTT:TPipe-Bedrock:0.0.1")
    implementation("com.TTT:TPipe-Defaults:0.0.1")

    // Align kotlinx-serialization with Kotlin 2.2.x
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

// Enforce consistent serialization versions across transitive deps
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

application {
    mainClass.set("com.example.tpipewriter.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runInferenceCli") {
    group = "application"
    description = "Runs the Bedrock Inference Config CLI"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("cli.InferenceConfigCli")
}