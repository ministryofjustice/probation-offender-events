package uk.gov.justice.digital.hmpps.offenderevents.health

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.GetTopicAttributesResult
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class TopicHealthTest {

  private val awsSnsClient = mock<AmazonSNS>()
  private val arn = "SOME_ARN"
  private val topicHealth = TopicHealth(awsSnsClient, arn)

  @Test
  fun `Topic health is up`() {
    whenever(awsSnsClient.getTopicAttributes("SOME_ARN")).thenReturn(GetTopicAttributesResult())

    val health = topicHealth.health()

    assertThat(health.status).isEqualTo(Status.UP)
    assertThat(health.details).containsEntry("arn", "SOME_ARN")
  }

  @Test
  fun `Topic health is down`() {
    whenever(awsSnsClient.getTopicAttributes("SOME_ARN")).thenThrow(RuntimeException())

    val health = topicHealth.health()

    assertThat(health.status).isEqualTo(Status.DOWN)
    assertThat(health.details).containsEntry("arn", "SOME_ARN")
  }
}
