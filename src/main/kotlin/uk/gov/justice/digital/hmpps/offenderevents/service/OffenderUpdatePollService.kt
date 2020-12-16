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
import java.time.LocalDateTime

@Service
class OffenderUpdatePollService(
  private val communityApiService: CommunityApiService,
  snsAwsClient: AmazonSNS,
  @Value("\${sns.topic.arn}") topicArn: String,
  private val objectMapper: ObjectMapper,
  private val telemetryService: TelemetryService
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val notificationMessagingTemplate = NotificationMessagingTemplate(snsAwsClient)
  private val topicMessageChannel = TopicMessageChannel(snsAwsClient, topicArn)
  private val sourceTableListForOffenderChangedEvent = listOf("ALIAS", "OFFENDER", "OFFENDER_MANAGER", "OFFENDER_ADDRESS")

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    telemetryService.offenderUpdatesPolled()
    do {
      val update: OffenderUpdate? = communityApiService.getOffenderUpdate()
        ?.also { telemetryService.offenderUpdateFound() }
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
        telemetryService.offenderUpdatePermanentlyFailed(offenderUpdate)
      } else {
        telemetryService.offenderUpdateFailed()
      }
    }

  private fun publishMessage(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers) {

    if(sourceTableListForOffenderChangedEvent.contains(offenderUpdate.sourceTable)) {
      notificationMessagingTemplate.convertAndSend(
        topicMessageChannel,
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
      ).also { telemetryService.offenderEventPublished() }
    }
    notificationMessagingTemplate.convertAndSend(
      topicMessageChannel,
      toOffenderEventJson(primaryIdentifiers, offenderUpdate),
      mapOf("eventType" to sourceToEventType(offenderUpdate.sourceTable), "source" to "delius")
    ).also { telemetryService.offenderEventPublished() }

    telemetryService.allOffenderEventsPublished(offenderUpdate, primaryIdentifiers)
  }

  private fun sourceToEventType(sourceTable: String): String = when (sourceTable) {
    "ALIAS" -> "OFFENDER_ALIAS_CHANGED"
    "OFFENDER" -> "OFFENDER_DETAILS_CHANGED"
    "OFFENDER_MANAGER" -> "OFFENDER_MANAGER_CHANGED"
    "OFFENDER_ADDRESS" -> "OFFENDER_ADDRESS_CHANGED"
    else -> "OFFENDER_${sourceTable}_CHANGED"
  }

  private fun logOffenderFound(offenderUpdate: OffenderUpdate) {
    log.info("Found offender update for offenderId=${offenderUpdate.offenderId}")
  }

  internal fun toOffenderEventJson(offenderIdentifiers: OffenderIdentifiers, offenderUpdate: OffenderUpdate? = null): String =
    objectMapper.writeValueAsString(
      OffenderEvent(
        offenderId = offenderIdentifiers.offenderId,
        crn = offenderIdentifiers.primaryIdentifiers.crn,
        eventDatetime = offenderUpdate?.dateChanged,
        nomsNumber = offenderIdentifiers.primaryIdentifiers.nomsNumber,
        sourceId = offenderUpdate?.sourceRecordId
      )
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OffenderEvent(val offenderId: Long, val crn: String, val nomsNumber: String? = null, val sourceId: Long? = null, val eventDatetime: LocalDateTime?)
