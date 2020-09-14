package uk.gov.justice.digital.hmpps.offenderevents.service
import org.springframework.http.HttpStatus.NOT_FOUND
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class CommunityApiService(
    @Qualifier("communityApiWebClient") private val webClient: WebClient,
    @Value("\${community.endpoint.url}") private val communityApiUrl: String,
) {

  fun getOffenderUpdate() : OffenderUpdate? {
    return webClient.get()
        .uri("$communityApiUrl/secure/offenders/nextUpdate")
        .retrieve()
        .bodyToMono(OffenderUpdate::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
        .block()
  }

  fun getOffenderIdentifiers(offenderId: Long): OffenderIdentifiers =
      webClient.get()
          .uri("$communityApiUrl/secure/offenders/offenderId/$offenderId/identifiers")
          .retrieve()
          .bodyToMono(OffenderIdentifiers::class.java)
          .block()!!

}

fun <T> emptyWhenNotFound(exception: WebClientResponseException): Mono<T> = emptyWhen(exception, NOT_FOUND)
fun <T> emptyWhen(exception: WebClientResponseException, statusCode: HttpStatus): Mono<T> =
    if (exception.rawStatusCode == statusCode.value()) Mono.empty() else Mono.error(exception)


data class OffenderUpdate(
    val offenderId: Long,
    val dateChanged: LocalDateTime,
    val action: String,
    val offenderDeltaId: Long,
    val sourceTable: String,
    val sourceRecordId: Long,
    val status: String
)

data class PrimaryIdentifiers (
  val crn: String,
  val nomsNumber: String,
)
data class OffenderIdentifiers(
    val offenderId: Long,
    val primaryIdentifiers: PrimaryIdentifiers,
)