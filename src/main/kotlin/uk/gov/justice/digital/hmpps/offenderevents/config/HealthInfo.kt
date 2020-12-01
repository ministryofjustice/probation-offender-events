package uk.gov.justice.digital.hmpps.offenderevents.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class HealthInfo(@param:Autowired private val buildProperties: BuildProperties) : HealthIndicator {
  override fun health(): Health {
    return try {
      Health.up().withDetail("version", version).build()
    } catch (e: RestClientException) {
      Health.down().withDetail("problem", e.message).build()
    }
  }

  private val version: String
    get() = buildProperties.version
}
