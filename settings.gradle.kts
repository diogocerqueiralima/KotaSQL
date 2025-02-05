plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "KotaSQL"
include("kota-sql-processor")
include("kota-sql-example")
