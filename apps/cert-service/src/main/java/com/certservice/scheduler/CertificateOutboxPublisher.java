package com.certservice.scheduler;

import com.certservice.enums.OutboxStatus;
import com.certservice.models.OutboxEvent;
import com.certservice.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload()).get();
                event.setStatus(OutboxStatus.SENT);
                outboxEventRepository.save(event);
                log.debug("Published outbox eventId={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox eventId={} topic={}", event.getId(), event.getTopic(), e);
            }
        }
    }
}
