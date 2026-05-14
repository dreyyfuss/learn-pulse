package com.userservice.kafka;

import com.userservice.domain.idempotency.IdempotencyLog;
import com.userservice.domain.user.User;
import com.userservice.kafka.dto.CertificateGeneratedEvent;
import com.userservice.kafka.dto.ModuleUnlockedEvent;
import com.userservice.kafka.dto.UserEnrolledEvent;
import com.userservice.repository.IdempotencyLogRepository;
import com.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final IdempotencyLogRepository idempotencyLogRepository;
    private final UserRepository           userRepository;
    private final MailgunClient            mailgunClient;

    @Transactional
    public void processUserEnrolled(UserEnrolledEvent event, String eventId, String topic) {
        User user = userRepository.findById(UUID.fromString(event.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

        mailgunClient.send(
                user.getEmail(),
                user.getFullName(),
                "enrolment_welcome",
                Map.of(
                        "userName", user.getFullName(),
                        "courseId", event.getCourseId(),
                        "enrolmentId", event.getEnrolmentId()
                )
        );

        idempotencyLogRepository.save(new IdempotencyLog(eventId, topic));
        log.info("Welcome email sent userId={} eventId={}", event.getUserId(), eventId);
    }

    @Transactional
    public void processModuleUnlocked(ModuleUnlockedEvent event, String eventId, String topic) {
        User user = userRepository.findById(UUID.fromString(event.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

        mailgunClient.send(
                user.getEmail(),
                user.getFullName(),
                "module_unlocked",
                Map.of(
                        "userName", user.getFullName(),
                        "moduleTitle", event.getUnlockedModuleTitle(),
                        "moduleOrder", event.getUnlockedModuleOrder()
                )
        );

        idempotencyLogRepository.save(new IdempotencyLog(eventId, topic));
        log.info("Module-unlocked email sent userId={} moduleTitle={} eventId={}",
                event.getUserId(), event.getUnlockedModuleTitle(), eventId);
    }

    @Transactional
    public void processCertificateGenerated(CertificateGeneratedEvent event, String eventId, String topic) {
        User user = userRepository.findById(UUID.fromString(event.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

        String learnerName = event.getLearnerName() != null && !event.getLearnerName().isBlank()
                ? event.getLearnerName() : user.getFullName();
        String courseName  = event.getCourseName()  != null && !event.getCourseName().isBlank()
                ? event.getCourseName() : event.getCourseId();

        mailgunClient.sendHtml(
                user.getEmail(),
                user.getFullName(),
                CertificateEmailBuilder.subject(courseName),
                CertificateEmailBuilder.html(
                        learnerName, courseName,
                        event.getDownloadUrl(), event.getCertificateId(), event.getIssuedAt()
                )
        );

        idempotencyLogRepository.save(new IdempotencyLog(eventId, topic));
        log.info("Certificate email sent userId={} certId={} eventId={}",
                event.getUserId(), event.getCertificateId(), eventId);
    }
}
