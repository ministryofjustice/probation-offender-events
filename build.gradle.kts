plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.1.3"
  kotlin("plugin.spring") version "1.6.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.2")

  implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.6.RELEASE")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.6")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.6")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.6")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.33.0")
  testImplementation("com.google.code.gson:gson:2.9.0")
  testImplementation("org.testcontainers:localstack:1.16.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "16"
    }
  }
}
