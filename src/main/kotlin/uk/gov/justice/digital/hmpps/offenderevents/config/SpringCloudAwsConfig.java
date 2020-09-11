package uk.gov.justice.digital.hmpps.offenderevents.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringCloudAwsConfig {

    @Bean
    @ConditionalOnProperty(name = "sns.provider", havingValue = "aws")
    @Primary
    public AmazonSNSAsync awsSnsClient(@Value("${sns.aws.access.key.id}") String accessKey, @Value("${sns.aws.secret.access.key}") String secretKey,
                                       @Value("${cloud.aws.region.static}") String region) {
        var creds = new BasicAWSCredentials(accessKey, secretKey);
        return AmazonSNSAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withRegion(region)
                .build();
    }
}
