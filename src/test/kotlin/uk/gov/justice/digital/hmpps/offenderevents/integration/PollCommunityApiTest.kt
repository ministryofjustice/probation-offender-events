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
    fun `2 offender events are written to the topic for each of the 3 updates`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 6 }
    }

    @Test
    fun `offender primary identifiers are retrieved from community api for each update`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { CommunityApiExtension.communityApi.countGetPrimaryIdentifiersRequests() } matches { it == offenderDeltaIds.size }

      offenderIds.forEach {
        CommunityApiExtension.communityApi.verifyPrimaryIdentifiersCalledWith(it)
      }
    }

    @Test
    fun `each offender update event written contains the CRN, offenderId and optionally the noms number`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == offenderDeltaIds.size * 2 }

      offenderIds.forEach {
        val genericMessage = getNextMessageOnTestQueue()

        assertThatJson(genericMessage.Message).node("offenderId").isEqualTo(it)
        assertThatJson(genericMessage.Message).node("crn").isEqualTo("CRN$it")
        assertThatJson(genericMessage.Message).node("nomsNumber").isEqualTo("NOMS$it")

        val specificMessage = getNextMessageOnTestQueue()

        assertThatJson(specificMessage.Message).node("offenderId").isEqualTo(it)
        assertThatJson(specificMessage.Message).node("crn").isEqualTo("CRN$it")
        assertThatJson(specificMessage.Message).node("nomsNumber").isEqualTo("NOMS$it")
      }
    }

    @Test
    fun `all messages have attributes for the event type and source`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == offenderDeltaIds.size * 2  }

      repeat(offenderDeltaIds.size * 2 ) {
        val message = getNextMessageOnTestQueue()
        assertThat(message.MessageAttributes.eventType.Value).isNotBlank
        assertThat(message.MessageAttributes.source.Value).isEqualTo("delius")
      }
    }

    @Test
    fun `3 offender details are deleted using community api`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { CommunityApiExtension.communityApi.countDeleteOffenderUpdateRequests() } matches { it == offenderDeltaIds.size }

      offenderDeltaIds.forEach {
        CommunityApiExtension.communityApi.verifyOffenderUpdateDeleteCalledWith(it)
      }
    }


    @Test
    fun `3 telemetry events will be raised`() {
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == offenderDeltaIds.size * 2}

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
        assertThat(attributesCaptor.allValues[index]).containsKey("timeSinceUpdateSeconds")
      }
    }
  }

  @Nested
  inner class EventTypes {
    @BeforeEach
    fun setUp() {
      CommunityApiExtension.communityApi.stubPrimaryIdentifiers(102L)
      CommunityApiExtension.communityApi.stubDeleteOffenderUpdate(1L)
      awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    }

    @Test
    internal fun `when source is OFFENDER_ADDRESS than address event is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "OFFENDER_ADDRESS"))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_ADDRESS_CHANGED")
    }
    @Test
    internal fun `when source is OFFENDER than offender details event is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "OFFENDER"))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_DETAILS_CHANGED")

    }

    @Test
    internal fun `when source is OFFENDER_MANAGER than offender manager event is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "OFFENDER_MANAGER"))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_MANAGER_CHANGED")
    }

    @Test
    internal fun `when source is ALIAS than offender alias event is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "ALIAS"))
      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_ALIAS_CHANGED")
    }

    @Test
    internal fun `when source is OFFICER than offender officer event is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "OFFICER"))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_OFFICER_CHANGED")
    }

    @Test
    internal fun `when source is unknown than offender event containing table name is raised along with generic event`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "BANANAS"))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }

      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_CHANGED")
      assertThat(getNextMessageOnTestQueue().MessageAttributes.eventType.Value).isEqualTo("OFFENDER_BANANAS_CHANGED")
    }

    @Test
    internal fun `source id is added to event for the sepecifc events`() {
      CommunityApiExtension.communityApi.stubNextUpdates(createOffenderUpdate(offenderDeltaId = 1L, offenderId = 102L, sourceTable = "ALIAS", sourceRecordId = 99L))

      offenderUpdatePollService.pollForOffenderUpdates()

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 2  }
      getNextMessageOnTestQueue()

      val message = getNextMessageOnTestQueue()
      assertThatJson(message.Message).node("sourceId").isEqualTo(99L)
    }
  }

  private fun getNextMessageOnTestQueue() =
      gson.fromJson(awsSqsClient.receiveMessage(queueUrl).messages[0].body, Message::class.java)


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

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 1 * 2 }

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

private fun createOffenderUpdate(offenderDeltaId: Long, offenderId: Long, failedUpdate: Boolean = false, sourceTable : String = "OFFENDER", sourceRecordId : Long = 345L) = OffenderUpdate(
    offenderId = offenderId,
    offenderDeltaId = offenderDeltaId,
    dateChanged = LocalDateTime.parse("2020-07-19T13:56:43"),
    action = "INSERT",
    sourceTable = sourceTable,
    sourceRecordId = sourceRecordId,
    status = "INPROGRESS",
    failedUpdate = failedUpdate
)


data class EventType(val Value: String)
data class Source(val Value: String)
data class MessageAttributes(val eventType: EventType, val source: Source)
data class Message(val Message: String, val MessageAttributes: MessageAttributes)
