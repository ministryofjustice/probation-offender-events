package uk.gov.justice.digital.hmpps.offenderevents.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.OAuthExtension

@ExtendWith(CommunityApiExtension::class, OAuthExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTestBase {

  @BeforeEach
  internal fun setUp() {
    Mockito.reset(telemetryClient)
  }

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @SpyBean
  lateinit var telemetryClient: TelemetryClient
}
