package uk.gov.justice.digital.hmpps.offenderevents.config

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class LocalStackConfig {
  @Bean
  @ConditionalOnProperty(name = ["sns.provider"], havingValue = "localstack", matchIfMissing = true)
  @Primary
  fun awsSnsClient(@Value("\${sns.endpoint.url}") serviceEndpoint: String, @Value("\${cloud.aws.region.static}") region: String): AmazonSNS =
      AmazonSNSClientBuilder.standard()
          .withEndpointConfiguration(EndpointConfiguration(serviceEndpoint, region))
          .build()
}