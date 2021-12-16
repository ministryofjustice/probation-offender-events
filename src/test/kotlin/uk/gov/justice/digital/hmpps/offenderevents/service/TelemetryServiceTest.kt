package uk.gov.justice.digital.hmpps.offenderevents.service

import com.microsoft.applicationinsights.TelemetryClient
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDateTime

class TelemetryServiceTest {

  private val telemetryClient = mock<TelemetryClient>()
  private val meterFactory = mock<MeterFactory>()
  private val pollCount = mock<Counter>()
  private val readCount = mock<Counter>()
  private val failedCount = mock<Counter>()
  private val permanentlyFailedCount = mock<Counter>()
  private val publishedCount = mock<Counter>()
  private val ageOfOffenderUpdate = mock<Timer>()

  @Test
  fun offenderUpdatesPolled() {
    whenever(meterFactory.pollCounter()).thenReturn(pollCount)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.offenderUpdatesPolled()

    verify(pollCount).increment()
  }

  @Test
  fun offenderUpdatesFound() {
    whenever(meterFactory.readCounter()).thenReturn(readCount)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.offenderUpdateFound()

    verify(readCount).increment()
  }

  @Test
  fun offenderUpdatesFailed() {
    whenever(meterFactory.failedCounter()).thenReturn(failedCount)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.offenderUpdateFailed()

    verify(failedCount).increment()
  }

  @Test
  fun offenderEventPublished() {
    whenever(meterFactory.publishedCounter()).thenReturn(publishedCount)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.offenderEventPublished()

    verify(publishedCount).increment()
  }

  @Test
  fun offenderEventPermanentlyFailed() {
    whenever(meterFactory.permanentlyFailedCounter()).thenReturn(permanentlyFailedCount)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.offenderUpdatePermanentlyFailed(anOffenderUpdate())

    verify(permanentlyFailedCount).increment()
    verify(telemetryClient).trackEvent(eq("ProbationOffenderPermanentlyFailedEvent"), anyMap(), isNull())
  }

  @Test
  fun allOffenderEventsPublished() {
    whenever(meterFactory.ageOfOffenderUpdateGauge()).thenReturn(ageOfOffenderUpdate)
    val telemetryService = TelemetryService(telemetryClient, meterFactory)

    telemetryService.allOffenderEventsPublished(anOffenderUpdate(), anOffenderIdentifier())

    verify(ageOfOffenderUpdate).record(any<Duration>())
    verify(telemetryClient).trackEvent(eq("ProbationOffenderEvent"), anyMap(), isNull())
  }
}

private fun anOffenderUpdate(
  offenderDeltaId: Long = 1L,
  offenderId: Long = 11L,
  failedUpdate: Boolean = false,
  sourceTable: String = "OFFENDER",
  sourceRecordId: Long = 345L,
  dateChanged: LocalDateTime = LocalDateTime.parse("2020-07-19T13:56:43"),
  status: String = "INPROGRESS"
) = OffenderUpdate(
  offenderId = offenderId,
  offenderDeltaId = offenderDeltaId,
  dateChanged = dateChanged,
  action = "INSERT",
  sourceTable = sourceTable,
  sourceRecordId = sourceRecordId,
  status = status,
  failedUpdate = failedUpdate
)

private fun anOffenderIdentifier(offenderId: Long = 1L) = OffenderIdentifiers(offenderId, PrimaryIdentifiers("crn$offenderId", "noms$offenderId"))
