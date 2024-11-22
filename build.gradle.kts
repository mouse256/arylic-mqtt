plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.allopen") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.quarkus")
    id("distribution")
}

repositories {
    mavenLocal()
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-smallrye-reactive-messaging-mqtt")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-jackson")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")
    implementation("xyz.gianlu.zeroconf:zeroconf:1.3.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.kotest:kotest-assertions-core:5.9.+")
    testImplementation("io.quarkiverse.mockk:quarkus-junit5-mockk:2.1.0")
}

group = "org.acme"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.javaParameters = true
}

