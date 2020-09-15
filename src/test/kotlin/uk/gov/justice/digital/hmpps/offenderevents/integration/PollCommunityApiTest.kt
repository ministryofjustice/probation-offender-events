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
import uk.gov.justice.digital.hmpps.offenderevents.service.OffenderUpdate
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension
import java.time.LocalDateTime


class PollCommunityApiTest : IntegrationTestBase() {

  @Autowired
  lateinit var queueUrl: String

  @Autowired
  lateinit var awsSqsClient: AmazonSQS

  private val gson = GsonBuilder().create()

  private val offenderUpdates = listOf(
      createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L),
      createOffenderUpdate(offenderDeltaId = 2L, offenderId = 103L),
      createOffenderUpdate(offenderDeltaId = 3L, offenderId = 1024)
  )
  private val offenderDeltaIds = offenderUpdates.map { it.offenderDeltaId }
  private val offenderIds = offenderUpdates.map { it.offenderId }

  private final fun createOffenderUpdate(offenderDeltaId: Long, offenderId: Long) = OffenderUpdate(
      offenderId = offenderId,
      offenderDeltaId = offenderDeltaId,
      dateChanged = LocalDateTime.now(),
      action = "INSERT",
      sourceTable = "OFFENDER",
      sourceRecordId = 345L,
      status = "INPROGRESS"
  )

  @BeforeEach
  fun setUp() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    CommunityApiExtension.communityApi.stubNextUpdates(*offenderUpdates.toTypedArray())
    CommunityApiExtension.communityApi.stubPrimaryIdentifiers(*offenderIds.toLongArray())
    CommunityApiExtension.communityApi.stubDeleteOffenderUpdate(*offenderDeltaIds.toLongArray())
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
    offenderIds.forEach {
      CommunityApiExtension.communityApi.verifyPrimaryIdentifiersCalledWith(it)
    }
  }

  @Test
  fun `3 offender events are written to the topic`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }

    offenderIds.forEach {
      val messageBody = awsSqsClient.receiveMessage(queueUrl).messages[0].body
      val message = gson.fromJson(messageBody, Message::class.java)

      assertThatJson(message.Message).node("offenderId").isEqualTo(it)
      assertThatJson(message.Message).node("crn").isEqualTo("CRN$it")
      assertThatJson(message.Message).node("nomsNumber").isEqualTo("NOMS$it")
    }
  }

  @Test
  fun `all messages have attributes for the event type and source`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }

    repeat(3) {
      val messageBody = awsSqsClient.receiveMessage(queueUrl).messages[0].body
      val message = gson.fromJson(messageBody, Message::class.java)
      assertThat(message.MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(message.MessageAttributes.source.Value).isEqualTo("delius")
    }
  }

  @Test
  fun `3 offender details are deleted using community api`() {
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }

    offenderDeltaIds.forEach {
      CommunityApiExtension.communityApi.verifyOffenderUpdateDeleteCalledWith(it)
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
