import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gg.grounds.base-conventions") version "0.8.0"
    kotlin("jvm") version "2.2.20"
    `maven-publish`
}

group = "gg.grounds"

version = findProperty("versionOverride")?.toString() ?: "0.1.0-SNAPSHOT"

val minestomVersion = "2026.07.22-26.2"

kotlin { jvmToolchain(25) }

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

// Minestom itself requires JVM 25+, so there is nothing to gain from a lower target.
tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_25) }

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username =
                providers.gradleProperty("github.user").orNull
                    ?: System.getenv("GITHUB_USER")
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
            password =
                providers.gradleProperty("github.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
        }
    }
}

dependencies {
    // The host server supplies Minestom at runtime; this library must never
    // drag a second copy in.
    compileOnly("net.minestom:minestom:$minestomVersion")
    // Per-player GUIs render per-player language; adventure itself comes from
    // Minestom (library-i18n declares it compileOnly), so nothing doubles up.
    api("gg.grounds:library-i18n:0.1.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    // compileOnly is not on the test classpath; the Click-dispatch tests
    // construct Minestom click records directly.
    testImplementation("net.minestom:minestom:$minestomVersion")
}

tasks.test { useJUnitPlatform() }

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/groundsgg/library-gui")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
