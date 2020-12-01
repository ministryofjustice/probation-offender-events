package uk.gov.justice.digital.hmpps.offenderevents.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
      communityMockServer.stubFor(
        get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(createOffenderUpdate(expectedOffenderUpdate))
            .withStatus(HTTP_OK)
        )
      )

      val actualOffenderUpdate = service.getOffenderUpdate()

      assertThat(actualOffenderUpdate).isEqualTo(expectedOffenderUpdate)
      communityMockServer.verify(
        getRequestedFor(urlEqualTo("/secure/offenders/nextUpdate"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `next update will be null if not found`() {
      communityMockServer.stubFor(
        get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      val offenderUpdate = service.getOffenderUpdate()

      assertThat(offenderUpdate).isNull()
    }

    @Test
    fun `next update will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        get("/secure/offenders/nextUpdate").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.getOffenderUpdate() }.isInstanceOf(WebClientResponseException.BadRequest::class.java)
    }
  }

  @Nested
  inner class GetOffenderIdentifiers {

    @BeforeEach
    fun `stub token`() {
      oAuthMockServer.stubGrantToken()
    }

    @Test
    fun `offender identifiers calls endpoint`() {
      val expectedOffenderIdentifier = createOffenderIdentifiers()
      communityMockServer.stubFor(
        get("/secure/offenders/offenderId/99/identifiers").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(createOffenderIdentifiers(expectedOffenderIdentifier))
            .withStatus(HTTP_OK)
        )
      )

      val actualOffenderIdentifier = service.getOffenderIdentifiers(99L)

      assertThat(actualOffenderIdentifier).isEqualTo(expectedOffenderIdentifier)
      communityMockServer.verify(
        getRequestedFor(urlEqualTo("/secure/offenders/offenderId/99/identifiers"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `offender identifiers does not include missing noms`() {
      val expectedOffenderIdentifier = createOffenderIdentifiersNoNoms()
      communityMockServer.stubFor(
        get("/secure/offenders/offenderId/99/identifiers").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(createOffenderIdentifiersNoNoms(expectedOffenderIdentifier))
            .withStatus(HTTP_OK)
        )
      )

      val actualOffenderIdentifier = service.getOffenderIdentifiers(99L)

      assertThat(actualOffenderIdentifier).isEqualTo(expectedOffenderIdentifier)
      communityMockServer.verify(
        getRequestedFor(urlEqualTo("/secure/offenders/offenderId/99/identifiers"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `get offender identifiers returns null if not found`() {
      communityMockServer.stubFor(
        get("/secure/offenders/offenderId/99/identifiers").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      assertThat(service.getOffenderIdentifiers(99L)).isNull()
    }

    @Test
    fun `get offender identifiers will throw exception for other types of http responses`() {
      communityMockServer.stubFor(
        get("/secure/offenders/offenderId/99/identifiers").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.getOffenderIdentifiers(99L) }.isInstanceOf(WebClientResponseException.BadRequest::class.java)
    }
  }

  @Nested
  inner class DeleteOffenderUpdate {

    @BeforeEach
    fun `stub token`() {
      oAuthMockServer.stubGrantToken()
    }

    @Test
    fun `delete calls endpoint`() {
      communityMockServer.stubFor(
        delete("/secure/offenders/update/101").willReturn(
          aResponse().withStatus(HTTP_OK)
        )
      )

      service.deleteOffenderUpdate(101L)

      communityMockServer.verify(deleteRequestedFor(urlEqualTo("/secure/offenders/update/101")).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `delete fails with not found`() {
      communityMockServer.stubFor(
        delete("/secure/offenders/update/101").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      assertThatThrownBy { service.deleteOffenderUpdate(101L) }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    fun `delete throws exception for other types of http responses`() {
      communityMockServer.stubFor(
        delete("/secure/offenders/update/101").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.deleteOffenderUpdate(101L) }.isInstanceOf(WebClientResponseException.BadRequest::class.java)
    }
  }
  @Nested
  inner class MarkOffenderUpdateAsPermanentlyFailed {

    @BeforeEach
    fun `stub token`() {
      oAuthMockServer.stubGrantToken()
    }

    @Test
    fun `mark as failed calls endpoint`() {
      communityMockServer.stubFor(
        put("/secure/offenders/update/101/markAsFailed").willReturn(
          aResponse().withStatus(HTTP_OK)
        )
      )

      service.markOffenderUpdateAsPermanentlyFailed(101L)

      communityMockServer.verify(putRequestedFor(urlEqualTo("/secure/offenders/update/101/markAsFailed")).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `mark as failed with not found`() {
      communityMockServer.stubFor(
        put("/secure/offenders/update/101/markAsFailed").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HTTP_NOT_FOUND)
        )
      )

      assertThatThrownBy { service.markOffenderUpdateAsPermanentlyFailed(101L) }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    fun `mark as failed throws exception for other types of http responses`() {
      communityMockServer.stubFor(
        put("/secure/offenders/update/101/markAsFailed").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.markOffenderUpdateAsPermanentlyFailed(101L) }.isInstanceOf(WebClientResponseException.BadRequest::class.java)
    }
  }

  private fun createOffenderUpdate(): OffenderUpdate {
    return OffenderUpdate(1L, LocalDateTime.now(), "UPSERT", 2L, "OFFENDER", 99L, "INPROGRESS", false)
  }

  private fun createOffenderUpdate(offenderUpdate: OffenderUpdate) =
    """
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

  private fun createOffenderIdentifiers(): OffenderIdentifiers {
    return OffenderIdentifiers(offenderId = 99, primaryIdentifiers = PrimaryIdentifiers(crn = "X12345", nomsNumber = "A12345A"))
  }

  private fun createOffenderIdentifiersNoNoms(): OffenderIdentifiers {
    return OffenderIdentifiers(offenderId = 99, primaryIdentifiers = PrimaryIdentifiers(crn = "X12345"))
  }

  private fun createOffenderIdentifiers(offenderIdentifiers: OffenderIdentifiers) =
    """
    {
      "offenderId": ${offenderIdentifiers.offenderId},
      "primaryIdentifiers": {
        "crn": "${offenderIdentifiers.primaryIdentifiers.crn}",
        "nomsNumber": "${offenderIdentifiers.primaryIdentifiers.nomsNumber}",
        "pncNumber": "2016/001225T"
      }
    }
    """.trimIndent()

  private fun createOffenderIdentifiersNoNoms(offenderIdentifiers: OffenderIdentifiers) =
    """
    {
      "offenderId": ${offenderIdentifiers.offenderId},
      "primaryIdentifiers": {
        "crn": "${offenderIdentifiers.primaryIdentifiers.crn}",
        "pncNumber": "2016/001225T"
      }
    }
    """.trimIndent()
}
