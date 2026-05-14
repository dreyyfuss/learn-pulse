package com.courseservice.events;

import com.courseservice.enums.OutboxStatus;
import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<OutboxEvent> pending =
                outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getPayload());
                if (event.getTraceId() != null) {
                    record.headers().add("trace-id",
                            event.getTraceId().getBytes(StandardCharsets.UTF_8));
                }
                kafkaTemplate.send(record).get();
                event.setStatus(OutboxStatus.SENT);
                outboxRepository.save(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("OutboxPublisher interrupted while sending event {}", event.getId());
                break;
            } catch (ExecutionException e) {
                log.error("Failed to publish outbox event {} to topic {}: {}",
                        event.getId(), event.getTopic(), e.getCause().getMessage());
                // Leave as PENDING — retried on next scheduler tick
            }
        }
    }
}
