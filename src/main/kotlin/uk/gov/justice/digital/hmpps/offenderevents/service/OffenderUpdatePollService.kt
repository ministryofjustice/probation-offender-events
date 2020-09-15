package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@Configuration
class OffenderUpdatePollService(
    private val communityApiService: CommunityApiService,
    private val snsAwsClient: AmazonSNS,
    @Value("\${sns.topic.arn}") private val topicArn: String,
    private val objectMapper: ObjectMapper
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    do {
      val update: Any? = communityApiService.getOffenderUpdate()
          .also { log.info("Found offender update for offenderId=${it?.offenderId}") }
          ?.let { communityApiService.getOffenderIdentifiers(it.offenderId) }
          ?.let {
            NotificationMessagingTemplate(snsAwsClient).convertAndSend(
                TopicMessageChannel(snsAwsClient, topicArn),
                toOffenderEventJson(it),
                mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
            )
            it
          }
    } while (update != null)

  }

  private fun toOffenderEventJson(offenderIdentifiers: OffenderIdentifiers): String =
      objectMapper.writeValueAsString(
          OffenderEvent(
              offenderId = offenderIdentifiers.offenderId,
              crn = offenderIdentifiers.primaryIdentifiers.crn,
              nomsNumber = offenderIdentifiers.primaryIdentifiers.nomsNumber
          ))


}

data class OffenderEvent(val offenderId: Long, val crn: String, val nomsNumber: String? = null)