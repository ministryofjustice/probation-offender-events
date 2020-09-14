plugins {
	id("uk.gov.justice.hmpps.gradle-spring-boot") version "1.0.2"
	kotlin("plugin.spring") version "1.4.0"
}

configurations {
	testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

	implementation("org.springframework.cloud:spring-cloud-starter-aws-messaging:2.2.1.RELEASE")

	testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.1")
	testImplementation("org.awaitility:awaitility-kotlin:4.0.3")

}
