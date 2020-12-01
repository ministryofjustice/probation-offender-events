package uk.gov.justice.digital.hmpps.offenderevents.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service

internal const val POLL_METRIC = "offender.poll"
internal const val UPDATES_METRIC = "offender.updates"
internal const val UPDATE_AGE_METRIC = "offender.update.age"

@Service
class MeterFactory(private val meterRegistry: MeterRegistry) {

  fun pollCounter() = registerCounter(meterRegistry, POLL_METRIC, "The number of polls")
  fun readCounter() = registerCounter(meterRegistry, UPDATES_METRIC, "The number of updates read", "read")
  fun failedCounter() = registerCounter(meterRegistry, UPDATES_METRIC, "The number of updates failed", "failed")
  fun permanentlyFailedCounter() = registerCounter(meterRegistry, UPDATES_METRIC, "The number of updates permanently failed - no furth retries", "permanentlyFailed")
  fun publishedCounter() = registerCounter(meterRegistry, UPDATES_METRIC, "The number of update events published", "published")
  fun ageOfOffenderUpdateGauge(): Timer = Timer.builder(UPDATE_AGE_METRIC).description("The age of the update before being published").register(meterRegistry)

  private fun registerCounter(meterRegistry: MeterRegistry, name: String, description: String, type: String = ""): Counter {
    val builder = Counter.builder(name).description(description)
    if (type.isNotEmpty()) {
      builder.tag("type", type)
    }
    return builder.register(meterRegistry)
  }
}
