package com.certservice.controllers;

import com.certservice.dto.response.ApiResponse;
import com.certservice.dto.response.CertificateResponse;
import com.certservice.models.Certificate;
import com.certservice.repositories.CertificateRepository;
import com.certservice.security.UserPrincipal;
import com.certservice.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Certificates", description = "Certificate retrieval and download")
@RestController
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateRepository certificateRepository;
    private final S3Service s3Service;

    @Operation(summary = "List my certificates", description = "Returns all certificates issued to the authenticated learner")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Certificate list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Authentication required")
    })
    @GetMapping("/api/learner/certificates")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> listMine(
            Authentication auth
    ) {

        String userId = principal(auth).getId();

        List<CertificateResponse> certs = certificateRepository
                .findByUserId(userId)
                .stream()
                .map(CertificateResponse::from)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success(certs, "OK")
        );
    }


    @Operation(summary = "Download certificate", description = "Redirects to a presigned S3 URL for the certificate PDF")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to presigned download URL"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Certificate not found")
    })
    @GetMapping("/api/certificates/{uuid}/download")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<?> download(
            @PathVariable String uuid,
            Authentication auth
    ) {
        String userId = principal(auth).getId();

        Optional<Certificate> maybeCert =
                certificateRepository.findByCertificateUuid(uuid);

        if (maybeCert.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("Certificate not found", "NOT_FOUND"));
        }

        Certificate cert = maybeCert.get();

        if (!cert.getUserId().equals(userId)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied", "FORBIDDEN"));
        }

        String presignedUrl = s3Service.presignedUrl(cert.getS3Key(), Duration.ofMinutes(5));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, presignedUrl)
                .build();
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}