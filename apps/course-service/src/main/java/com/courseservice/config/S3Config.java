package com.courseservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${app.s3.endpoint:}")
    private String endpoint;

    @Value("${app.s3.region:us-east-1}")
    private String region;

    @Bean
    public S3Client s3Client(
            @Value("${app.s3.access-key:minioadmin}") String accessKey,
            @Value("${app.s3.secret-key:minioadmin}") String secretKey) {

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            // MinIO (and other S3-compatible stores) require an explicit endpoint override
            // and path-style access — virtual-hosted-style (bucket.host) is not supported
            // by default in MinIO.
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true);
        }

        return builder.build();
    }
}
