package uk.gov.justice.digital.hmpps.offenderevents.integration

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension

class PollCommunityApiTest : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    CommunityApiExtension.communityApi.stubNextUpdates((1L to 2L), (2L to 3L), (3L to 4L))
  }

  @Test
  fun `Community API is called 3 times`() {
    await untilCallTo { CommunityApiExtension.communityApi.countNextUpdateRequests() } matches { it == 3 }
  }
}