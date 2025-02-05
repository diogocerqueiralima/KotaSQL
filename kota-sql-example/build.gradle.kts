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
    implementation(libs.logback)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(project(":kota-sql-processor"))
    ksp(project(":kota-sql-processor"))
}

kotlin {
    jvmToolchain(23)
}