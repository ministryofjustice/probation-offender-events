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
      val update: OffenderUpdate? = communityApiService.getOffenderUpdate()
          ?.also { logOffenderFound(it) }
          ?.apply { processUpdate(this) }
    } while (update != null)

  }

  private fun processUpdate(offenderUpdate: OffenderUpdate) =
      communityApiService.getOffenderIdentifiers(offenderUpdate.offenderId)?.run {
        publishMessage(offenderUpdate, this)
        communityApiService.deleteOffenderUpdate(offenderUpdate.offenderDeltaId)
      } ?: run {
        if (offenderUpdate.failedUpdate) {
          communityApiService.markOffenderUpdateAsPermanentlyFailed(offenderUpdate.offenderDeltaId)
          telemetryClient.trackEvent(
              "ProbationOffenderPermanentlyFailedEvent",
              mapOf(
                  "offenderDeltaId" to offenderUpdate.offenderDeltaId.toString(),
                  "offenderId" to offenderUpdate.offenderId.toString()
              ),
              null
          )
        }
      }

  private fun publishMessage(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers) {
    NotificationMessagingTemplate(snsAwsClient).convertAndSend(
        TopicMessageChannel(snsAwsClient, topicArn),
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
    )
    recordAnalytics(offenderUpdate, primaryIdentifiers)
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

  private fun logOffenderFound(offenderUpdate: OffenderUpdate) {
    log.info("Found offender update for offenderId=${offenderUpdate.offenderId}")
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