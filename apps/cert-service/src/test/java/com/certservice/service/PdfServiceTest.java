package com.certservice.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.consumer.auto-startup=false",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:pdftest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.s3.endpoint=",
        "app.s3.access-key=test",
        "app.s3.secret-key=test",
        "app.s3.bucket=test",
        "app.s3.region=us-east-1"
})
class PdfServiceTest {

    @Autowired PdfService pdfService;
    @MockBean  KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void generateCertificate_returnsNonEmptyPdfBytes() {
        byte[] pdf = pdfService.generateCertificate(
                "Jane Doe",
                "Java Fundamentals",
                "John Instructor",
                "test-cert-uuid-1234",
                LocalDate.of(2025, 1, 15)
        );

        assertThat(pdf).isNotNull().isNotEmpty();
        // PDF files start with %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateCertificate_differentInputs_produceDifferentOutput() {
        byte[] pdf1 = pdfService.generateCertificate(
                "Alice", "Course A", "Instructor X", "uuid-1", LocalDate.now());
        byte[] pdf2 = pdfService.generateCertificate(
                "Bob", "Course B", "Instructor Y", "uuid-2", LocalDate.now());

        assertThat(pdf1).isNotEqualTo(pdf2);
    }

    @Test
    void renderSample_writesToDisk() throws Exception {
        byte[] pdf = pdfService.generateCertificate(
                "Jane Doe", "Advanced Java & Spring Boot",
                "Prof. John Instructor", "sample-uuid-0001",
                LocalDate.of(2026, 5, 12));

        Path out = Path.of("target", "sample-cert.pdf");
        Files.write(out, pdf);
        log.info("Sample certificate written → {}", out.toAbsolutePath());

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(out.toFile());
        }

        assertThat(Files.size(out)).isGreaterThan(0L);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
