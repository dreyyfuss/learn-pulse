package com.courseservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket:learnpulse}")
    private String bucket;

    public String presignUploadUrl(String key, String mimeType, Duration ttl) {
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(r -> r.bucket(bucket).key(key).contentType(mimeType))
                .build();
        return s3Presigner.presignPutObject(req).url().toString();
    }

    public String presignDownloadUrl(String key, Duration ttl) {
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build();
        return s3Presigner.presignGetObject(req).url().toString();
    }

    public void putObject(String key, byte[] data, String mimeType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
                    RequestBody.fromBytes(data));
            log.info("Uploaded s3://{}/{} ({} bytes)", bucket, key, data.length);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload object: " + key, e);
        }
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        log.info("Deleted s3://{}/{}", bucket, key);
    }
}
