package com.courseservice.services;

import com.courseservice.models.Course;
import com.courseservice.models.User;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ITemplateEngine templateEngine;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        UserRepository userRepository,
                        CourseRepository courseRepository,
                        ITemplateEngine templateEngine,
                        @Value("${app.mailgun.from:noreply@learnpulse.com}") String from) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.templateEngine = templateEngine;
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
        Long courseId = toLong(payload.get("courseId"));
        String certificateId = String.valueOf(payload.get("certificateId"));
        String s3Url = String.valueOf(payload.get("s3Url"));

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot send certificate email — user {} not found", userId);
            return;
        }

        String courseName = courseRepository.findById(courseId)
                .map(Course::getTitle)
                .orElse("your course");

        Context ctx = new Context();
        ctx.setVariable("learnerName", user.getFullName());
        ctx.setVariable("courseName", courseName);
        ctx.setVariable("certificateId", certificateId);
        ctx.setVariable("downloadUrl", s3Url);
        ctx.setVariable("issuedAt", formatDate(String.valueOf(payload.get("issuedAt"))));

        String html = templateEngine.process("certificate_delivery", ctx);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject("Your LearnPulse Certificate is Ready!");
            helper.setText(html, true);
            mailSender.send(message);
            log.debug("Certificate delivery email sent to {}", user.getEmail());
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send certificate delivery email to " + user.getEmail(), e);
        }
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

    private static String formatDate(String isoDateTime) {
        try {
            return LocalDateTime.parse(isoDateTime).format(DATE_FMT);
        } catch (Exception e) {
            return isoDateTime;
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Long l)    return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n)  return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
