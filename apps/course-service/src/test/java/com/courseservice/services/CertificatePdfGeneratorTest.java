package com.courseservice.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

    private final CertificatePdfGenerator generator = new CertificatePdfGenerator();

    @Test
    void generate_returnsBytesThatStartWithPdfMagicBytes() {
        CertificateModel model = new CertificateModel(
                "550e8400-e29b-41d4-a716-446655440000",
                "Jane Doe",
                "Introduction to Spring Boot",
                "John Smith",
                "May 9, 2026",
                "May 9, 2026"
        );

        byte[] pdf = generator.generate(model);

        // PDF files always begin with the magic bytes %PDF-
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
