package uk.gov.justice.digital.hmpps.offenderevents.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@Configuration
class OffenderUpdatePollService(private val communityApiService: CommunityApiService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(fixedDelayString = "\${offenderUpdatePoll.fixedDelay.ms}")
  fun pollForOffenderUpdates() {
    communityApiService.getOffenderUpdate()
        .let { log.info("Found offender update for offenderId=${it?.offenderId}") }
  }
}