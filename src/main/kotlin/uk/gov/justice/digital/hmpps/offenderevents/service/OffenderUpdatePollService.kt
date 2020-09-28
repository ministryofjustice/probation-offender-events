package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
    meterRegistry: MeterRegistry
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val notificationMessagingTemplate = NotificationMessagingTemplate(snsAwsClient)
  private val topicMessageChannel = TopicMessageChannel(snsAwsClient, topicArn)
  private val pollCount  = Counter.builder("offender.poll")
      .description("The number of polls")
      .register(meterRegistry)
  private val updatesReadCount  = Counter.builder("offender.updates")
      .tag("type", "read")
      .description("The number of updates read")
      .register(meterRegistry)
  private val updatesFailedCount  = Counter.builder("offender.updates")
      .tag("type", "failed")
      .description("The number of updates failed")
      .register(meterRegistry)
  private val updatesPublishedCount  = Counter.builder("offender.updates")
      .tag("type", "published")
      .description("The number of updates published")
      .register(meterRegistry)
  private val ageOfOffenderUpdate = Timer.builder("offender.update.age" )
      .description("The age of the update before being published")
      .register(meterRegistry)

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    pollCount.increment()
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
          updatesFailedCount.increment()
        }
      }

  private fun publishMessage(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers) {
    notificationMessagingTemplate.convertAndSend(
        topicMessageChannel,
        toOffenderEventJson(primaryIdentifiers),
        mapOf("eventType" to "OFFENDER_CHANGED", "source" to "delius")
    ).also { updatesPublishedCount.increment() }
    notificationMessagingTemplate.convertAndSend(
        topicMessageChannel,
        toOffenderEventJson(primaryIdentifiers, offenderUpdate),
        mapOf("eventType" to sourceToEventType(offenderUpdate.sourceTable), "source" to "delius")
    ).also { updatesPublishedCount.increment() }
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
    val age = Duration.between(offenderUpdate.dateChanged, LocalDateTime.now())
    ageOfOffenderUpdate.record(age)

    telemetryClient.trackEvent(
        "ProbationOffenderEvent",
        mapOf(
            "crn" to primaryIdentifiers.primaryIdentifiers.crn,
            "action" to offenderUpdate.action,
            "offenderDeltaId" to offenderUpdate.offenderDeltaId.toString(),
            "source" to offenderUpdate.sourceTable,
            "sourceId" to offenderUpdate.sourceRecordId.toString(),
            "dateChanged" to offenderUpdate.dateChanged.format(DateTimeFormatter.ISO_DATE_TIME),
            "timeSinceUpdateSeconds" to age.toSeconds().toString()
        ),
        null
    )
  }

  private fun logOffenderFound(offenderUpdate: OffenderUpdate) {
    log.info("Found offender update for offenderId=${offenderUpdate.offenderId}")
    updatesReadCount.increment()
  }

  internal fun toOffenderEventJson(offenderIdentifiers: OffenderIdentifiers, offenderUpdate: OffenderUpdate? = null): String =
      objectMapper.writeValueAsString(
          OffenderEvent(
              offenderId = offenderIdentifiers.offenderId,
              crn = offenderIdentifiers.primaryIdentifiers.crn,
              eventDatetime = offenderUpdate?.dateChanged,
              nomsNumber = offenderIdentifiers.primaryIdentifiers.nomsNumber,
              sourceId = offenderUpdate?.sourceRecordId
          ))


}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OffenderEvent(val offenderId: Long, val crn: String, val nomsNumber: String? = null, val sourceId: Long? = null, val eventDatetime: LocalDateTime?)