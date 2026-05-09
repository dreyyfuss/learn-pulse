package com.certservice.controller;

import com.certservice.dto.CertificateResponse;
import com.certservice.model.Certificate;
import com.certservice.repository.CertificateRepository;
import com.certservice.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateRepository certificateRepository;
    private final StorageService storageService;

    @GetMapping("/api/learner/certificates")
    public List<CertificateResponse> listMyCertificates(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return certificateRepository.findAllByUserIdOrderByIssuedAtDesc(userId)
                .stream()
                .map(c -> new CertificateResponse(
                        c.getId(),
                        c.getCourseId(),
                        c.getEnrolmentId(),
                        "/api/certificates/" + c.getId() + "/download",
                        c.getIssuedAt()))
                .toList();
    }

    @GetMapping("/api/certificates/{id}/download")
    public ResponseEntity<Void> download(@PathVariable String id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));

        if (!cert.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        String presignedUrl = storageService.generatePresignedUrl(cert.getS3Key(), Duration.ofMinutes(5));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(presignedUrl))
                .build();
    }
}
