plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.3.0"
  kotlin("plugin.spring") version "1.6.21"
  id("com.google.cloud.tools.jib") version "3.2.1"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.3")

  implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.6.RELEASE")

  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.micrometer:micrometer-core")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.9")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.9")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.9")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.35.0")
  testImplementation("com.google.code.gson:gson:2.9.0")
  testImplementation("org.testcontainers:localstack:1.17.2")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
  val copyAgentJar by registering(Copy::class) {
    from("${project.buildDir}/libs")
    include("applicationinsights-agent*.jar")
    into("${project.buildDir}/agent")
    rename("applicationinsights-agent(.+).jar", "agent.jar")
    dependsOn("assemble")
  }

  val jib by getting {
    dependsOn += copyAgentJar
  }

  val jibBuildTar by getting {
    dependsOn += copyAgentJar
  }

  val jibDockerBuild by getting {
    dependsOn += copyAgentJar
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}

jib {
  container {
    creationTime = "USE_CURRENT_TIMESTAMP"
    jvmFlags = mutableListOf("-Duser.timezone=Europe/London")
    mainClass = "uk.gov.justice.digital.hmpps.offenderevents.ProbationOffenderEventsKt"
    user = "2000:2000"
  }
  from {
    image = "eclipse-temurin:17-jre-alpine"
  }
  extraDirectories {
    paths {
      path {
        setFrom("${project.buildDir}")
        includes.add("agent/agent.jar")
      }
      path {
        setFrom("${project.rootDir}")
        includes.add("applicationinsights*.json")
        setInto("/agent")
      }
    }
  }
}
