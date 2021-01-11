plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "2.1.2"
  kotlin("plugin.spring") version "1.4.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

  implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.5.RELEASE")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")

  implementation("org.springdoc:springdoc-openapi-ui:1.5.1")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.1")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.1")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.22.0")
  testImplementation("com.google.code.gson:gson:2.8.6")
}
