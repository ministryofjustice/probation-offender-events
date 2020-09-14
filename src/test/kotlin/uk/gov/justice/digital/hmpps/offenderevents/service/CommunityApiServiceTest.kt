package uk.gov.justice.digital.hmpps.offenderevents.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.offenderevents.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.OAuthExtension
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.time.LocalDateTime

class CommunityApiServiceTest : IntegrationTestBase() {

  private val communityMockServer = CommunityApiExtension.communityApi
  private val oAuthMockServer = OAuthExtension.oAuthApi

  @Autowired
  private lateinit var service: CommunityApiService

  @Nested
  inner class GetUpdates {

    @BeforeEach
    fun `stub token`() {
      oAuthMockServer.stubGrantToken()
    }

    @Test
    fun `next update calls endpoint`() {
      val expectedOffenderUpdate = createOffenderUpdate()
      communityMockServer.stubFor(get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(createOffenderUpdate(expectedOffenderUpdate))
              .withStatus(HTTP_OK)))

      val actualOffenderUpdate = service.getOffenderUpdate()

      assertThat(actualOffenderUpdate).isEqualTo(expectedOffenderUpdate)
      communityMockServer.verify(getRequestedFor(urlEqualTo("/secure/offenders/nextUpdate"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `next update will be null if not found`() {
      communityMockServer.stubFor(get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody("{\"error\": \"not found\"}")
              .withStatus(HTTP_NOT_FOUND)))

      val offenderUpdate = service.getOffenderUpdate()

      assertThat(offenderUpdate).isNull()
    }

    @Test
    fun `next update will throw exception for other types of http responses`() {
      communityMockServer.stubFor(get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HTTP_BAD_REQUEST)))

      assertThatThrownBy { service.getOffenderUpdate() }.isInstanceOf(WebClientResponseException.BadRequest::class.java)
    }
  }

  private fun createOffenderUpdate(): OffenderUpdate {
    return OffenderUpdate(1L, LocalDateTime.now(), "UPSERT", 2L, "OFFENDER", 99L, "INPROGRESS")
  }

  private fun createOffenderUpdate(offenderUpdate: OffenderUpdate) = """
    {
      "offenderId": ${offenderUpdate.offenderId},
      "dateChanged": "${offenderUpdate.dateChanged}",
      "action": "${offenderUpdate.action}",
      "offenderDeltaId": ${offenderUpdate.offenderDeltaId},
      "sourceTable": "${offenderUpdate.sourceTable}",
      "sourceRecordId": ${offenderUpdate.sourceRecordId},
      "status": "${offenderUpdate.status}"
    }
  """.trimIndent()

}