package com.certservice.controllers;

import com.certservice.repositories.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCertController {

    private final CertificateRepository certificateRepository;

    @GetMapping("/certificates/{uuid}/exists")
    public ResponseEntity<Map<String, Boolean>> exists(@PathVariable String uuid) {
        boolean exists = certificateRepository.findByCertificateUuid(uuid).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
