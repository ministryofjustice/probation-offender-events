package uk.gov.justice.digital.hmpps.offenderevents.config

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LocalStackTestConfig {
  @Bean
  fun awsSqsClient(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${cloud.aws.region.static}") region: String
  ): AmazonSQS = AmazonSQSClientBuilder.standard()
    .withEndpointConfiguration(EndpointConfiguration(serviceEndpoint, region))
    .build()

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(
    @Autowired awsSqsClient: AmazonSQS,
    @Value("\${sqs.queue.name}") queueName: String
  ): String = awsSqsClient.getQueueUrl(queueName).queueUrl
}
