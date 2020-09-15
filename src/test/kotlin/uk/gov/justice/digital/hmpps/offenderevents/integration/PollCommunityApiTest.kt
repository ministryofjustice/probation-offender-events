package uk.gov.justice.digital.hmpps.offenderevents.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.google.gson.GsonBuilder
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
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
      val messageBody = awsSqsClient.receiveMessage(queueUrl).messages[0].body;
      val message = gson.fromJson(messageBody, Message::class.java)

      assertThatJson(message.Message).node("offenderId").isEqualTo(it)
      assertThatJson(message.Message).node("crn").isEqualTo("CRN$it")
      assertThatJson(message.Message).node("nomsNumber").isEqualTo("NOMS$it")
    }
  }

  @Test
  fun `message has attributes for the event type and source`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
    repeat(listOf(102L, 103L, 104L).size) {
      val messageBody = awsSqsClient.receiveMessage(queueUrl).messages[0].body;
      val message = gson.fromJson(messageBody, Message::class.java)
      assertThat(message.MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(message.MessageAttributes.source.Value).isEqualTo("delius")
    }
  }

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }
}

data class EventType(val Value: String)
data class Source(val Value: String)
data class MessageAttributes(val eventType: EventType, val source: Source)
data class Message(val Message: String, val MessageAttributes: MessageAttributes)
