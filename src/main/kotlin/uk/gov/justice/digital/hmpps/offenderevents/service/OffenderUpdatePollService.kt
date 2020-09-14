package uk.gov.justice.digital.hmpps.offenderevents.service

import com.amazonaws.services.sns.AmazonSNS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@Configuration
class OffenderUpdatePollService(
    private val communityApiService: CommunityApiService,
    private val snsAwsClient: AmazonSNS,
    @Value("\${sns.topic.arn}") private val topicArn: String
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    do {
      val update: OffenderUpdate? = communityApiService.getOffenderUpdate()
          .also { log.info("Found offender update for offenderId=${it?.offenderId}") }
          ?.let {
            NotificationMessagingTemplate(snsAwsClient).convertAndSend(
                TopicMessageChannel(snsAwsClient, topicArn),
                """{"hello": "world"}"""
            )
            it
          }
    } while (update != null)

  }
}