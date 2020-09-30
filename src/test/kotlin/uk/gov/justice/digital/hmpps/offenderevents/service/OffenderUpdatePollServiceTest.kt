package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDateTime

@SpringBootTest(classes = [ObjectMapper::class, OffenderUpdatePollService::class])
class OffenderUpdatePollServiceTest {

  @Suppress("unused")
  @MockBean
  lateinit var communityApiService: CommunityApiService

  @Suppress("unused")
  @MockBean
  lateinit var amazonSNS: AmazonSNS

  @Suppress("unused")
  @MockBean
  lateinit var telemetryService: TelemetryService

  @Autowired
  lateinit var offenderUpdatePollService: OffenderUpdatePollService

  @Test
  fun `toOffenderEventJson - will populate noms number`() {
    val offenderEventJson = offenderUpdatePollService.toOffenderEventJson(OffenderIdentifiers(1L, PrimaryIdentifiers(crn = "crn", nomsNumber = "noms")))

    assertThatJson(offenderEventJson).isEqualTo("""
      {
        "offenderId": 1,
        "crn": "crn",
        "nomsNumber": "noms"
      }
    """.trimIndent())
  }

  @Test
  fun `toOffenderEventJson - will not populate missing noms number`() {
    val offenderEventJson = offenderUpdatePollService.toOffenderEventJson(OffenderIdentifiers(1L, PrimaryIdentifiers(crn = "crn")))

    assertThatJson(offenderEventJson).isEqualTo("""
      {
        "offenderId": 1,
        "crn": "crn"
      }
    """.trimIndent())
  }


  @Test
  fun `pollForOffenderUpdates - will notify telemetry service - happy path`() {
    val offenderUpdate = anOffenderUpdate(offenderId = 1L, offenderDeltaId = 11L)
    whenever(communityApiService.getOffenderUpdate())
        .thenReturn(offenderUpdate)
        .thenReturn(null)
    whenever(communityApiService.getOffenderIdentifiers(offenderUpdate.offenderId)).thenReturn(anOffenderIdentifier(offenderUpdate.offenderId))

    offenderUpdatePollService.pollForOffenderUpdates()

    verify(telemetryService).offenderUpdatesPolled()
    verify(telemetryService).offenderUpdateFound()
    verify(telemetryService, times(2)).offenderEventPublished()
    verify(telemetryService).allOffenderEventsPublished(any(), any())
  }

  @Test
  fun `pollForOffenderUpdates - will notify telemetry service - failure`() {
    val offenderUpdate = anOffenderUpdate(offenderId = 1L, offenderDeltaId = 11L)
    whenever(communityApiService.getOffenderUpdate())
        .thenReturn(offenderUpdate)
        .thenReturn(null)
    whenever(communityApiService.getOffenderIdentifiers(offenderUpdate.offenderId)).thenReturn(null)

    offenderUpdatePollService.pollForOffenderUpdates()

    verify(telemetryService).offenderUpdatesPolled()
    verify(telemetryService).offenderUpdateFound()
    verify(telemetryService).offenderUpdateFailed()
  }

  @Test
  fun `pollForOffenderUpdates - will notify telemetry service - permanent failure`() {
    val offenderUpdate = anOffenderUpdate(offenderId = 1L, offenderDeltaId = 11L, failedUpdate = true)
    whenever(communityApiService.getOffenderUpdate())
        .thenReturn(offenderUpdate)
        .thenReturn(null)
    whenever(communityApiService.getOffenderIdentifiers(offenderUpdate.offenderId)).thenReturn(null)

    offenderUpdatePollService.pollForOffenderUpdates()

    verify(telemetryService).offenderUpdatesPolled()
    verify(telemetryService).offenderUpdateFound()
    verify(telemetryService).offenderUpdatePermanentlyFailed(any())
  }


}

private fun anOffenderUpdate(offenderDeltaId: Long,
                             offenderId: Long,
                             failedUpdate: Boolean = false,
                             sourceTable : String = "OFFENDER",
                             sourceRecordId : Long = 345L,
                             dateChanged : LocalDateTime = LocalDateTime.parse("2020-07-19T13:56:43"),
                             status : String = "INPROGRESS"
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

private fun anOffenderIdentifier(offenderId: Long) = OffenderIdentifiers(offenderId, PrimaryIdentifiers("crn$offenderId", "noms$offenderId"))