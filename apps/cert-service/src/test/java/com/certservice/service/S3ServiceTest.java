package com.certservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock S3Client    s3Client;
    @Mock S3Presigner s3Presigner;

    @InjectMocks S3Service s3Service;

    @BeforeEach
    void setUp() throws Exception {
        var field = S3Service.class.getDeclaredField("bucket");
        field.setAccessible(true);
        field.set(s3Service, "learnpulse");
    }

    @Test
    void upload_callsPutObjectWithCorrectKeyAndContentType() {
        byte[] content = {1, 2, 3, 4};

        s3Service.upload("certificates/user/course/cert.pdf", content, "application/pdf");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCaptor.capture(), any(RequestBody.class));
        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("learnpulse");
        assertThat(req.key()).isEqualTo("certificates/user/course/cert.pdf");
        assertThat(req.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void presignedUrl_callsPresignerAndReturnsUrlString() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://minio.local/learnpulse/cert.pdf?sig=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = s3Service.presignedUrl("certificates/cert.pdf", Duration.ofMinutes(5));

        assertThat(url).contains("minio.local").contains("sig=abc");
    }
}
