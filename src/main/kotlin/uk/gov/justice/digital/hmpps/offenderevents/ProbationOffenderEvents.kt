package uk.gov.justice.digital.hmpps.offenderevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRestClientAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [ReactiveElasticsearchRestClientAutoConfiguration::class,
      ReactiveElasticsearchRepositoriesAutoConfiguration::class]
)
class ProbationOffenderEvents

fun main(args: Array<String>) {
  runApplication<ProbationOffenderEvents>(*args)
}