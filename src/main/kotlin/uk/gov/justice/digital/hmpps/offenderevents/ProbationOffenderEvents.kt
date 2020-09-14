package uk.gov.justice.digital.hmpps.offenderevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ProbationOffenderEvents

fun main(args: Array<String>) {
  runApplication<ProbationOffenderEvents>(*args)
}