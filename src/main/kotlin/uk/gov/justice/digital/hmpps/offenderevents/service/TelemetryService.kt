package uk.gov.justice.digital.hmpps.offenderevents.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class TelemetryService(private val telemetryClient: TelemetryClient,
                       meterFactory: MeterFactory) {

  private val pollCount  = meterFactory.pollCounter()
  private val foundCount  = meterFactory.readCounter()
  private val failedCount  = meterFactory.failedCounter()
  private val permanentlyFailedCount  = meterFactory.permanentlyFailedCounter()
  private val publishedCount  = meterFactory.publishedCounter()
  private val ageOfOffenderUpdate = meterFactory.ageOfOffenderUpdateGauge()

  internal fun offenderUpdatesPolled() = pollCount.increment()
  internal fun offenderUpdateFound() = foundCount.increment()
  internal fun offenderUpdateFailed() = failedCount.increment()
  internal fun offenderEventPublished() = publishedCount.increment()

  internal fun offenderUpdatePermanentlyFailed(offenderUpdate: OffenderUpdate) {
    telemetryClient.trackEvent(
        "ProbationOffenderPermanentlyFailedEvent",
        mapOf(
            "offenderDeltaId" to offenderUpdate.offenderDeltaId.toString(),
            "offenderId" to offenderUpdate.offenderId.toString()
        ),
        null
    )
    permanentlyFailedCount.increment()
  }

  internal fun allOffenderEventsPublished(offenderUpdate: OffenderUpdate, primaryIdentifiers: OffenderIdentifiers) {
    val age = Duration.between(offenderUpdate.dateChanged, LocalDateTime.now())
    ageOfOffenderUpdate.record(Duration.between(offenderUpdate.dateChanged, LocalDateTime.now()))

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

}