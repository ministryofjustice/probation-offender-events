package uk.gov.justice.digital.hmpps.offenderevents.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["offenderUpdatePoll.enabled"], havingValue = "true")
class SchedulingConfig
