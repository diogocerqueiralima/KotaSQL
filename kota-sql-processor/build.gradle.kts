plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

group = "com.github.diogocerqueiralima"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(23)
}

publishing {

    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/diogocerqueiralima/KotaSQL")
            credentials {
                username = System.getenv("USERNAME_GITHUB")
                password = System.getenv("TOKEN_GITHUB")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }

}