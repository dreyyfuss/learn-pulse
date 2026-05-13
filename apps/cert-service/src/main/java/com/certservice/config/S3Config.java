package com.certservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${app.s3.endpoint:}")
    private String endpoint;

    @Value("${app.s3.public-endpoint:}")
    private String publicEndpoint;

    @Value("${app.s3.region:us-east-1}")
    private String region;

    @Value("${app.s3.access-key:minioadmin}")
    private String accessKey;

    @Value("${app.s3.secret-key:minioadmin}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true); // required for MinIO
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        // Use public-endpoint for presigned URLs so browsers can reach them directly.
        String presignHost = publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (!presignHost.isBlank()) {
            builder.endpointOverride(URI.create(presignHost))
                   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
