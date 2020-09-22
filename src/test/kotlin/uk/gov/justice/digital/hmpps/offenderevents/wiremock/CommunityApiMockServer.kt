package uk.gov.justice.digital.hmpps.offenderevents.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.offenderevents.service.OffenderUpdate
import java.time.format.DateTimeFormatter


class CommunityApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val communityApi = CommunityApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    communityApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    communityApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    communityApi.stop()
  }
}

class CommunityApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 9096
  }

  fun stubHealthPing(status: Int) {
    stubFor(get("/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))

  }

  fun stubNextUpdates(vararg offenderUpdates: OffenderUpdate) {
    offenderUpdates.forEachIndexed { index, offenderUpdate ->
      stubFor(get("/secure/offenders/nextUpdate")
          .inScenario("Multiple events")
          .whenScenarioStateIs(if (index == 0) STARTED else "$index")
          .willReturn(
              aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withBody(toJson(offenderUpdate))
          )
          .willSetStateTo("${index + 1}")
      )
    }
    stubFor(get("/secure/offenders/nextUpdate")
        .inScenario("Multiple events")
        .whenScenarioStateIs("${offenderUpdates.size}")
        .willReturn(
            aResponse()
                .withHeader("Content-Type", "application/json")
                .withStatus(404)
        )
        .willSetStateTo("FINISHED")
    )

  }

  fun stubDeleteOffenderUpdate(vararg offenderDeltaIds: Long) {
    offenderDeltaIds.forEach {
      stubFor(delete("/secure/offenders/update/$it").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)))
    }
  }

  fun stubMarkAsFailed(vararg offenderDeltaIds: Long) {
    offenderDeltaIds.forEach {
      stubFor(put("/secure/offenders/update/$it/markAsFailed").willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)))
    }
  }

  fun stubPrimaryIdentifiers(vararg offenderIds: Long) {
    offenderIds.forEach { offenderId ->
      stubFor(get("/secure/offenders/offenderId/${offenderId}/identifiers")
          .willReturn(
              aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withBody(anOffenderIdentifier(offenderId))
          )
      )
    }
  }

  fun stubPrimaryIdentifiersNotFound(vararg offenderIds: Long) {
    offenderIds.forEach { offenderId ->
      stubFor(get("/secure/offenders/offenderId/${offenderId}/identifiers")
          .willReturn(
              aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withStatus(404)
          )
      )
    }
  }

  fun verifyPrimaryIdentifiersCalledWith(offenderId: Long) = this.verify(getRequestedFor(urlEqualTo("/secure/offenders/offenderId/${offenderId}/identifiers")))

  fun verifyOffenderUpdateDeleteCalledWith(offenderDeltaId: Long) = this.verify(deleteRequestedFor(urlEqualTo("/secure/offenders/update/${offenderDeltaId}")))

  fun verifyNotMarkedAsFailed(offenderDeltaId: Long) = this.verify(exactly(0), putRequestedFor(urlEqualTo("/secure/offenders/update/$offenderDeltaId/markAsFailed")))

  fun verifyNotDeleteOffenderUpdate(offenderDeltaId: Long) = this.verify(exactly(0), deleteRequestedFor(urlEqualTo("/secure/offenders/update/$offenderDeltaId")))

  fun verifyMarkedAsFailed(offenderDeltaId: Long) = this.verify(putRequestedFor(urlEqualTo("/secure/offenders/update/$offenderDeltaId/markAsFailed")))

  fun verifyDeleteOffenderUpdate(offenderDeltaId: Long) = this.verify(deleteRequestedFor(urlEqualTo("/secure/offenders/update/$offenderDeltaId")))


  fun countNextUpdateRequests(): Int = findAll(getRequestedFor(urlEqualTo("/secure/offenders/nextUpdate"))).count()

  fun countGetPrimaryIdentifiersRequests(): Int = findAll(getRequestedFor(urlMatching("/secure/offenders/offenderId/[0-9]*/identifiers"))).count()

  fun countDeleteOffenderUpdateRequests(): Int = findAll(deleteRequestedFor(urlMatching("/secure/offenders/update/[0-9]*"))).count()

  private fun toJson(offenderUpdate: OffenderUpdate) = """
    {
      "offenderId": ${offenderUpdate.offenderId},
      "dateChanged": "${offenderUpdate.dateChanged.format(DateTimeFormatter.ISO_DATE_TIME)}",
      "action": "${offenderUpdate.action}",
      "offenderDeltaId": ${offenderUpdate.offenderDeltaId},
      "sourceTable": "${offenderUpdate.sourceTable}",
      "sourceRecordId": ${offenderUpdate.sourceRecordId},
      "status": "${offenderUpdate.status}",
      "failedUpdate": ${offenderUpdate.failedUpdate}
    }
  """.trimIndent()

  private fun anOffenderIdentifier(offenderId: Long) = """
    {
      "offenderId": $offenderId,
      "primaryIdentifiers": {
        "crn": "CRN${offenderId}",
        "nomsNumber": "NOMS${offenderId}"
      }
    }
  """.trimIndent()
}
