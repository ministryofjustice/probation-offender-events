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
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class OffenderUpdatePollService(
    private val communityApiService: CommunityApiService,
    snsAwsClient: AmazonSNS,
    @Value("\${sns.topic.arn}") topicArn: String,
    private val objectMapper: ObjectMapper,
    private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val notificationMessagingTemplate = NotificationMessagingTemplate(snsAwsClient)
  private val topicMessageChannel = TopicMessageChannel(snsAwsClient, topicArn)

  @Scheduled(fixedDelayString = "\${random.int(\${offenderUpdatePoll.fixedDelay.max.ms})}")
  fun pollForOffenderUpdates() {
    log.info(">>>>>>>>>>>>>>>>> poll started ${LocalDateTime.now()} <<<<<<<<<<<<<<<<<<<")
    do {
      val update: OffenderUpdate? = communityApiService.getOffenderUpdate()
          ?.also { logOffenderFound(it) }
          ?.apply { processUpdate(this) }
    } while (update != null)
    log.info(">>>>>>>>>>>>>>>>> poll ended ${LocalDateTime.now()} <<<<<<<<<<<<<<<<<<<")
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
    notificationMessagingTemplate.convertAndSend(
        topicMessageChannel,
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
    )
    notificationMessagingTemplate.convertAndSend(
        topicMessageChannel,
        toOffenderEventJson(primaryIdentifiers, offenderUpdate),
        mapOf("eventType" to sourceToEventType(offenderUpdate.sourceTable), "source" to "delius")
    )
    recordAnalytics(offenderUpdate, primaryIdentifiers)
  }

  private fun sourceToEventType(sourceTable: String): String = when(sourceTable) {
    "ALIAS" -> "OFFENDER_ALIAS_CHANGED"
    "OFFENDER" -> "OFFENDER_DETAILS_CHANGED"
    "OFFENDER_MANAGER" -> "OFFENDER_MANAGER_CHANGED"
    "OFFENDER_ADDRESS" -> "OFFENDER_ADDRESS_CHANGED"
    else -> "OFFENDER_${sourceTable}_CHANGED"
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
            "timeSinceUpdateSeconds" to Duration.between(offenderUpdate.dateChanged, LocalDateTime.now()).toSeconds().toString()
        ),
        null
    )
  }

  private fun logOffenderFound(offenderUpdate: OffenderUpdate) {
    log.info("Found offender update for offenderId=${offenderUpdate.offenderId}")
  }

  internal fun toOffenderEventJson(offenderIdentifiers: OffenderIdentifiers, offenderUpdate: OffenderUpdate? = null): String =
      objectMapper.writeValueAsString(
          OffenderEvent(
              offenderId = offenderIdentifiers.offenderId,
              crn = offenderIdentifiers.primaryIdentifiers.crn,
              nomsNumber = offenderIdentifiers.primaryIdentifiers.nomsNumber,
              sourceId = offenderUpdate?.sourceRecordId
          ))


}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OffenderEvent(val offenderId: Long, val crn: String, val nomsNumber: String? = null, val sourceId: Long? = null)