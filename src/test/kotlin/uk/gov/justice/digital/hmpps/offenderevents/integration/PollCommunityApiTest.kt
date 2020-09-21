package uk.gov.justice.digital.hmpps.offenderevents.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.google.gson.GsonBuilder
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.offenderevents.service.OffenderUpdate
import uk.gov.justice.digital.hmpps.offenderevents.service.OffenderUpdatePollService
import uk.gov.justice.digital.hmpps.offenderevents.wiremock.CommunityApiExtension
import java.time.LocalDateTime


class PollCommunityApiTest : IntegrationTestBase() {

  @Autowired
  lateinit var queueUrl: String

  @Autowired
  lateinit var awsSqsClient: AmazonSQS

  @Autowired
  lateinit var offenderUpdatePollService: OffenderUpdatePollService

  private val gson = GsonBuilder().create()

  @Nested
  inner class HappyPath {
    private val offenderUpdates = listOf(
        createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L),
        createOffenderUpdate(offenderDeltaId = 2L, offenderId = 103L),
        createOffenderUpdate(offenderDeltaId = 3L, offenderId = 1024)
    )
    private val offenderDeltaIds = offenderUpdates.map { it.offenderDeltaId }
    private val offenderIds = offenderUpdates.map { it.offenderId }

    @Captor
    private lateinit var attributesCaptor: ArgumentCaptor<Map<String, String>>

    @BeforeEach
    fun setUp() {
      awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
      CommunityApiExtension.communityApi.stubNextUpdates(*offenderUpdates.toTypedArray())
      CommunityApiExtension.communityApi.stubPrimaryIdentifiers(*offenderIds.toLongArray())
      CommunityApiExtension.communityApi.stubDeleteOffenderUpdate(*offenderDeltaIds.toLongArray())
    }

    @Test
    fun `Community API is called 4 times`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { CommunityApiExtension.communityApi.countNextUpdateRequests() } matches { it == 4 }
    }

    @Test
    fun `3 messages are added to the queue`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
    }

    @Test
    fun `3 offender details are retrieved from community api`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }
      offenderIds.forEach {
        CommunityApiExtension.communityApi.verifyPrimaryIdentifiersCalledWith(it)
      }
    }

    @Test
    fun `3 offender events are written to the topic`() {
      offenderUpdatePollService.pollForOffenderUpdates()

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
      offenderUpdatePollService.pollForOffenderUpdates()

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
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }

      offenderDeltaIds.forEach {
        CommunityApiExtension.communityApi.verifyOffenderUpdateDeleteCalledWith(it)
      }
    }


    @Test
    fun `3 telemetry events will be raised`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 3 }

      offenderIds.forEachIndexed { index, offenderId ->
        verify(telemetryClient, times(offenderIds.size)).trackEvent(
            eq("ProbationOffenderEvent"),
            attributesCaptor.capture(),
            isNull()
        )

        assertThat(attributesCaptor.allValues[index]).containsAllEntriesOf(mapOf(
            "crn" to "CRN$offenderId",
            "action" to "INSERT",
            "source" to "OFFENDER",
            "sourceId" to "345",
            "dateChanged" to "2020-07-19T13:56:43"
        ))
      }
    }
  }

  @Nested
  inner class ExceptionScenarios {
    @BeforeEach
    fun setUp() {
      awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
      CommunityApiExtension.communityApi.stubPrimaryIdentifiersNotFound(102L)
      CommunityApiExtension.communityApi.stubDeleteOffenderUpdate(1L)
      CommunityApiExtension.communityApi.stubMarkAsFailed(1L)
    }

    @Test
    internal fun `a new update which has no primary identifiers will not be deleted or marked as failed`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, failedUpdate = false))

      offenderUpdatePollService.pollForOffenderUpdates()


      CommunityApiExtension.communityApi.verifyNotMarkedAsFailed(1L)
      CommunityApiExtension.communityApi.verifyNotDeleteOffenderUpdate(1L)
    }

    @Test
    internal fun `for a new update which has no primary identifiers, the next available update will be retrieved`() {
      CommunityApiExtension.communityApi.stubNextUpdates(
          createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, failedUpdate = false),
          createOffenderUpdate(offenderDeltaId = 2L, offenderId = 202L, failedUpdate = false)
      )
      CommunityApiExtension.communityApi.stubPrimaryIdentifiersNotFound(102L)
      CommunityApiExtension.communityApi.stubPrimaryIdentifiers(202L)
      CommunityApiExtension.communityApi.stubDeleteOffenderUpdate(2L)

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 1 }

      CommunityApiExtension.communityApi.verifyNotDeleteOffenderUpdate(1L)
      CommunityApiExtension.communityApi.verifyDeleteOffenderUpdate(2L)
    }

    @Test
    internal fun `for a previously failed update which has no primary identifiers, the update is marked as permanently failed`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, failedUpdate = true))
      CommunityApiExtension.communityApi.stubPrimaryIdentifiersNotFound(102L)

      offenderUpdatePollService.pollForOffenderUpdates()

      CommunityApiExtension.communityApi.verifyMarkedAsFailed(1L)
      CommunityApiExtension.communityApi.verifyNotDeleteOffenderUpdate(1L)
    }

    @Test
    internal fun `when an update is marked as permanently failed a telemetry event is raised`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, failedUpdate = true))
      CommunityApiExtension.communityApi.stubPrimaryIdentifiersNotFound(102L)

      offenderUpdatePollService.pollForOffenderUpdates()

      verify(telemetryClient).trackEvent(
          eq("ProbationOffenderPermanentlyFailedEvent"),
          check {
            assertThat(it).containsExactlyEntriesOf(mapOf("offenderDeltaId" to "1", "offenderId" to "102"))
          },
          isNull()
      )
    }
  }

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }
}

private fun createOffenderUpdate(offenderDeltaId: Long, offenderId: Long, failedUpdate: Boolean = false) = OffenderUpdate(
    offenderId = offenderId,
    offenderDeltaId = offenderDeltaId,
    dateChanged = LocalDateTime.parse("2020-07-19T13:56:43"),
    action = "INSERT",
    sourceTable = "OFFENDER",
    sourceRecordId = 345L,
    status = "INPROGRESS",
    failedUpdate = failedUpdate
)


data class EventType(val Value: String)
data class Source(val Value: String)
data class MessageAttributes(val eventType: EventType, val source: Source)
data class Message(val Message: String, val MessageAttributes: MessageAttributes)
