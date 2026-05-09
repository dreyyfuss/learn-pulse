package com.courseservice.services;

import com.courseservice.events.dto.CertificateGeneratedEvent;
import com.courseservice.models.Certificate;
import com.courseservice.repositories.CertificateRepository;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CertificatePdfGenerator pdfGenerator;
    private final StorageService storageService;
    private final OutboxService outboxService;

    @Transactional
    public Certificate issue(Long userId, Long courseId, Long enrolmentId) {
        // Idempotent — return existing cert if already generated for this enrolment
        return certificateRepository.findByEnrolmentId(enrolmentId)
                .orElseGet(() -> generate(userId, courseId, enrolmentId));
    }

    private Certificate generate(Long userId, Long courseId, Long enrolmentId) {
        var learner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        var instructor = userRepository.findById(course.getInstructorId())
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + course.getInstructorId()));

        String certId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        CertificateModel model = new CertificateModel(
                certId,
                learner.getFullName(),
                course.getTitle(),
                instructor.getFullName(),
                DATE_FMT.format(LocalDate.now()),
                DATE_FMT.format(now)
        );

        byte[] pdf = pdfGenerator.generate(model);
        String s3Key = "certificates/" + certId + ".pdf";
        String s3Url = storageService.upload(s3Key, pdf, "application/pdf");

        Certificate cert = Certificate.builder()
                .id(certId)
                .userId(userId)
                .courseId(courseId)
                .enrolmentId(enrolmentId)
                .s3Key(s3Key)
                .build();
        certificateRepository.save(cert);

        outboxService.publish("certificate.generated", certId,
                new CertificateGeneratedEvent(
                        UUID.randomUUID().toString(),
                        "certificate.generated",
                        1,
                        now.toString(),
                        userId,
                        courseId,
                        certId,
                        s3Url,
                        now.toString()
                ));

        return cert;
    }
}
