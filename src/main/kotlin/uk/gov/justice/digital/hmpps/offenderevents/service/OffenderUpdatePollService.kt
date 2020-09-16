package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class OffenderUpdatePollService(
    private val communityApiService: CommunityApiService,
    private val snsAwsClient: AmazonSNS,
    @Value("\${sns.topic.arn}") private val topicArn: String,
    private val objectMapper: ObjectMapper,
    private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    do {
      val update: Any? = communityApiService.getOffenderUpdate()
          .also { logOffenderFound(it) }
          ?.let { publishMessage(it, communityApiService.getOffenderIdentifiers(it.offenderId)) }
          ?.also { communityApiService.deleteOffenderUpdate(it.offenderDeltaId) }
    } while (update != null)

  }

  private fun publishMessage(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers): OffenderUpdate {
    NotificationMessagingTemplate(snsAwsClient).convertAndSend(
        TopicMessageChannel(snsAwsClient, topicArn),
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
    )
    recordAnalytics(offenderUpdate, primaryIdentifiers)
    return offenderUpdate
  }

  private fun recordAnalytics(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers) {
    telemetryClient.trackEvent(
        "ProbationOffenderEvent",
        mapOf(
            "crn" to primaryIdentifiers.primaryIdentifiers.crn,
            "action" to offenderUpdate.action,
            "source" to offenderUpdate.sourceTable,
            "sourceId" to offenderUpdate.sourceRecordId.toString(),
            "dateChanged" to offenderUpdate.dateChanged.format(DateTimeFormatter.ISO_DATE_TIME),
        ),
        null
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