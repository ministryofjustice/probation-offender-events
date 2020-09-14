package uk.gov.justice.digital.hmpps.offenderevents.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension

// TODO minimise number of tests
// TODO convert map entryies into a data class to help readability
class PollCommunityApiTest : IntegrationTestBase() {

  @Autowired
  lateinit var queueUrl: String

  @Autowired
  lateinit var awsSqsClient: AmazonSQS

  @Autowired
  lateinit var objectMapper: ObjectMapper

  private val gson = GsonBuilder().create()

  @BeforeEach
  fun setUp() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    CommunityApiExtension.communityApi.stubNextUpdates((1L to 102L), (2L to 103L), (3L to 104L))
    CommunityApiExtension.communityApi.stubPrimaryIdentifiers(102L, 103L, 104L)
  }

  @Test
  fun `Community API is called 4 times`() {
    await untilCallTo { CommunityApiExtension.communityApi.countNextUpdateRequests() } matches { it == 4 }
  }

  @Test
  fun `3 messages are added to the queue`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
  }

  @Test
  fun `3 offender details are retrieved from community api`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
    listOf(102L, 103L, 104L).forEach {
      CommunityApiExtension.communityApi.verifyPrimaryIdentifiersCalledWith(it)
    }
  }

  @Test
  fun `3 offender events are written to the topic`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
    listOf(102L, 103L, 104L).forEach {
      val m= awsSqsClient.receiveMessage(queueUrl).messages[0].body;
      val message = gson.fromJson(m, Message::class.java)

      assertThatJson(message.Message).node("offenderId").isEqualTo(it)
      assertThatJson(message.Message).node("crn").isEqualTo("CRN$it")
      assertThatJson(message.Message).node("nomsNumber").isEqualTo("NOMS$it")
    }
  }

  @Test
  @Disabled
  fun `message gas attributes for the event type and source`() {
    TODO()
  }

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }
}

@JsonIgnoreProperties
data class Message(var Message: String)