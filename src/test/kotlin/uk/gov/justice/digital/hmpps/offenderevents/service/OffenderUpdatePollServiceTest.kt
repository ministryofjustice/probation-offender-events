package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(classes = [ObjectMapper::class, OffenderUpdatePollService::class, SimpleMeterRegistry::class])
class OffenderUpdatePollServiceTest {

  @Suppress("unused")
  @MockBean
  lateinit var communityApiService: CommunityApiService

  @Suppress("unused")
  @MockBean
  lateinit var amazonSNS: AmazonSNS


  @Suppress("unused")
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var offenderUpdatePollService: OffenderUpdatePollService

  @Test
  fun `will populate noms number`() {
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
  fun `will not populate missing noms number`() {
    val offenderEventJson = offenderUpdatePollService.toOffenderEventJson(OffenderIdentifiers(1L, PrimaryIdentifiers(crn = "crn")))

    assertThatJson(offenderEventJson).isEqualTo("""
      {
        "offenderId": 1,
        "crn": "crn"
      }
    """.trimIndent())
  }

}