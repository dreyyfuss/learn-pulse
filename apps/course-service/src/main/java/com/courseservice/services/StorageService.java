package com.courseservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${app.s3.bucket:learnpulse}")
    private String bucket;

    @Value("${app.s3.endpoint:}")
    private String endpoint;

    @Value("${app.s3.region:us-east-1}")
    private String region;

    /**
     * Uploads {@code content} to the configured bucket under {@code key} and returns
     * the public URL at which the object is accessible.
     *
     * @param key         S3 object key, e.g. {@code "certificates/cert-abc-123.pdf"}
     * @param content     raw bytes to upload
     * @param contentType MIME type, e.g. {@code "application/pdf"}
     * @return public URL of the uploaded object
     */
    public String upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
        return buildUrl(key);
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /**
     * Constructs the public URL for an object key without making a network call.
     * Uses MinIO path-style ({@code endpoint/bucket/key}) when an endpoint is configured;
     * falls back to the AWS virtual-hosted-style URL otherwise.
     */
    public String buildUrl(String key) {
        if (endpoint != null && !endpoint.isBlank()) {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return base + "/" + bucket + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }
}
