package uk.gov.justice.digital.hmpps.offenderevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.aws.autoconfigure.messaging.MessagingAutoConfiguration

@SpringBootApplication(exclude = [MessagingAutoConfiguration::class])
class ProbationOffenderEvents

fun main(args: Array<String>) {
  runApplication<ProbationOffenderEvents>(*args)
}
