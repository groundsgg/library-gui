plugins {
    // No version: the root build already puts the Kotlin plugin on the shared buildscript
    // classpath, and repeating a version for a plugin that is already there fails the build.
    kotlin("jvm")
    application
}

// base-conventions is applied to the root project only and configures nothing here, so this
// project inherits no toolchain, no repositories and no formatting. Everything it needs is local.
kotlin { jvmToolchain(25) }

repositories {
    mavenCentral()
    // Needed even though nothing here declares a GitHub Packages coordinate: the root exposes
    // library-i18n as an api dependency, which arrives transitively through project(":").
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
    implementation(project(":"))
    // The library declares Minestom compileOnly, so a runnable example has to bring a real one.
    implementation("net.minestom:minestom:2026.07.22-26.2")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.18")

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

application { mainClass.set("gg.grounds.gui.demo.MainKt") }

// Artwork and the generated pack are resolved relative to this project, not the repository root.
tasks.named<JavaExec>("run") { workingDir = projectDir }

// Nothing consumes a distribution of the demo; keep it off CI's and the release path's clock.
tasks.named("distTar") { enabled = false }

tasks.named("distZip") { enabled = false }
