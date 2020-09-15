package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
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
          .also { logOffenderFound(it) }
          ?.let { (it.offenderDeltaId to communityApiService.getOffenderIdentifiers(it.offenderId)) }
          ?.let { (offenderDeltaId, primaryIdentifiers) ->
            publishMessage(primaryIdentifiers)
            offenderDeltaId
          }?.also { communityApiService.deleteOffenderUpdate(it) }
    } while (update != null)

  }

  private fun publishMessage(primaryIdentifiers: OffenderIdentifiers) {
    NotificationMessagingTemplate(snsAwsClient).convertAndSend(
        TopicMessageChannel(snsAwsClient, topicArn),
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
    )
  }

  private fun logOffenderFound(it: OffenderUpdate?) {
    log.info(
        it?.let { "Found offender update for offenderId=${it.offenderId}" }
            ?: "No offender update found")
  }

  internal fun toOffenderEventJson(offenderIdentifiers: OffenderIdentifiers): String =
      objectMapper.writeValueAsString(
          OffenderEvent(
              offenderId = offenderIdentifiers.offenderId,
              crn = offenderIdentifiers.primaryIdentifiers.crn,
              nomsNumber = offenderIdentifiers.primaryIdentifiers.nomsNumber
          ))


}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OffenderEvent(val offenderId: Long, val crn: String, val nomsNumber: String? = null)