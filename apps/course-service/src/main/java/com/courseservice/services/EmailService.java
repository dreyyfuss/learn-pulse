package com.courseservice.services;

import com.courseservice.models.User;
import com.courseservice.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        UserRepository userRepository,
                        @Value("${app.mailgun.from:noreply@learnpulse.com}") String from) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.from = from;
    }

    public void sendEnrolmentWelcome(Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String subject = "Welcome to your new course!";
        String body = String.format(
                "Hi! You have been successfully enrolled in course %s (enrolment #%s). Good luck!",
                payload.get("courseId"), payload.get("enrolmentId"));
        sendToUser(userId, subject, body);
    }

    public void sendModuleUnlocked(Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String moduleTitle = String.valueOf(payload.get("unlockedModuleTitle"));
        String subject = "Next module ready: " + moduleTitle;
        String body = String.format(
                "Great progress! Module \"%s\" is now unlocked in your course. Keep it up!",
                moduleTitle);
        sendToUser(userId, subject, body);
    }

    public void sendCertificateDelivery(Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String subject = "Your certificate is ready!";
        String body = String.format(
                "Congratulations! Your certificate for course %s is ready. Download it at /api/certificates/%s/download.",
                payload.get("courseId"), payload.get("certificateId"));
        sendToUser(userId, subject, body);
    }

    private void sendToUser(Long userId, String subject, String body) {
        String to = userRepository.findById(userId)
                .map(User::getEmail)
                .orElse(null);

        if (to == null) {
            log.warn("Cannot send email '{}' — user {} not found", subject, userId);
            return;
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);

        mailSender.send(msg); // MailException propagates → triggers DLQ retries in EmailConsumer
        log.debug("Email sent to {} subject='{}'", to, subject);
    }

    private static Long toLong(Object value) {
        if (value instanceof Long l)    return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n)  return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
