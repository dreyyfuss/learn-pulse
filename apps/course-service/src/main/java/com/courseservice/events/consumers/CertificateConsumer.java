package com.courseservice.events.consumers;

import com.courseservice.services.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateConsumer {

    private final CertificateService certificateService;

    @KafkaListener(
            topics = "course.completed",
            groupId = "${spring.kafka.consumer.group-id:course-service}-cert",
            containerFactory = "emailContainerFactory"
    )
    public void onCourseCompleted(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        Map<String, Object> payload = record.value();
        if (payload == null) {
            log.warn("Null payload on course.completed at offset {} — discarding", record.offset());
            ack.acknowledge();
            return;
        }

        Long userId    = toLong(payload.get("userId"));
        Long courseId  = toLong(payload.get("courseId"));
        Long enrolmentId = toLong(payload.get("enrolmentId"));

        if (userId == null || courseId == null || enrolmentId == null) {
            log.warn("Missing required fields in course.completed at offset {} — discarding", record.offset());
            ack.acknowledge();
            return;
        }

        log.info("Generating certificate for enrolment {}", enrolmentId);
        certificateService.issue(userId, courseId, enrolmentId);
        ack.acknowledge();
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
