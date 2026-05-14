package com.certservice.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Testcontainers
class S3SmokeTest {

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-05-01T01-11-10Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    static S3Service s3Service;
    static S3Client  s3Client;

    static final String BUCKET = "learnpulse";

    @BeforeAll
    static void setUp() throws Exception {
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minio.getUserName(), minio.getPassword()));
        URI endpoint = URI.create(minio.getS3URL());

        s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(creds)
                .endpointOverride(endpoint)
                .forcePathStyle(true)
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        var presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(creds)
                .endpointOverride(endpoint)
                .build();

        s3Service = new S3Service(s3Client, presigner);

        var f = S3Service.class.getDeclaredField("bucket");
        f.setAccessible(true);
        f.set(s3Service, BUCKET);
    }

    @Test
    void upload_putsObjectIntoMinio() {
        byte[] pdf = "fake-pdf-content".getBytes();

        assertThatNoException()
                .isThrownBy(() -> s3Service.upload("smoke/test.pdf", pdf, "application/pdf"));

        HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder().bucket(BUCKET).key("smoke/test.pdf").build());
        assertThat(head.contentLength()).isEqualTo(pdf.length);
    }
}