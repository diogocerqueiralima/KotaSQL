plugins {
    kotlin("jvm") version "2.1.0"
    alias(libs.plugins.ksp)
}

group = "com.github.diogocerqueiralima"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(project(":processor"))
    ksp(project(":processor"))
}

kotlin {
    jvmToolchain(23)
}