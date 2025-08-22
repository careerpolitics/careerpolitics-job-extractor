package com.careerpolitics.scraper.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Value("${AWS_UPLOAD_REGION:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    @Value("${AWS_ID:}")
    private String accessKeyId;

    @Value("${AWS_SECRET:}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        AwsCredentialsProvider provider;
        if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
            provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        } else {
            provider = DefaultCredentialsProvider.create();
        }
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(provider)
                .build();
    }
}
