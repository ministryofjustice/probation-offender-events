package uk.gov.justice.digital.hmpps.offenderevents.health

import com.amazonaws.services.sns.AmazonSNS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class TopicHealth(
    private val awsSnsClient: AmazonSNS,
    @Value("\${sns.topic.arn}") private val arn: String
) : HealthIndicator {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun health(): Health =
      try {
        awsSnsClient.getTopicAttributes(arn)
        Health.Builder().up().withDetail("arn", arn).build()
      } catch (ex: Exception) {
        log.error("Health failed for SNS Topic due to ", ex)
        Health.Builder().down(ex).withDetail("arn", arn).build()
      }
}