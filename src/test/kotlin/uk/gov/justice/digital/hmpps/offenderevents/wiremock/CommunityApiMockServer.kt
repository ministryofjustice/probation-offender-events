package uk.gov.justice.digital.hmpps.offenderevents.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class CommunityApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val communityApi = CommunityApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    communityApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    communityApi.resetRequests()
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

  fun stubNextUpdates(vararg offenderUpdates: Pair<Long, Long>) {
    offenderUpdates.forEachIndexed { index, offenderUpdate ->
      stubFor(get("/secure/offenders/nextUpdate")
          .inScenario("Multiple events")
          .whenScenarioStateIs(if (index == 0) STARTED else "$index")
          .willReturn(
              aResponse()
                  .withHeader("Content-Type", "application/json")
                  .withBody(anOffenderUpdate(offenderUpdate.first, offenderUpdate.second))
          )
          .willSetStateTo("${index+1}")
      )
    }
  }

  fun countNextUpdateRequests() : Int = findAll(getRequestedFor(urlEqualTo("/secure/offenders/nextUpdate"))).count()

  private fun anOffenderUpdate(offenderDeltaId: Long, offenderId: Long) = """
    {
      "offenderId": $offenderId,
      "dateChanged": "2020-09-10T15:12:43.000Z",
      "action": "UPSERT",
      "offenderDeltaId": $offenderDeltaId,
      "sourceTable": "OFFENDER",
      "sourceRecordId": 345,
      "status": "INPROGRESS"
    }
  """.trimIndent()
}
