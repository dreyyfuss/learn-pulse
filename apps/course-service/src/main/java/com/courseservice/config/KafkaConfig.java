package com.courseservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${app.kafka.email.backoff.initial-ms:1000}")
    private long backoffInitialMs;

    @Value("${app.kafka.email.backoff.multiplier:4.0}")
    private double backoffMultiplier;

    @Value("${app.kafka.email.backoff.max-elapsed-ms:300000}")
    private long backoffMaxElapsedMs;

    // ── Producer ─────────────────────────────────────────────────────────────

    /**
     * Idempotent producer factory (kafka-events.md §4.4, §5.2):
     *   acks=all              — all in-sync replicas must acknowledge
     *   enable.idempotence    — exactly-once delivery per partition
     *   max.in.flight=5       — maximum allowed with idempotence enabled
     *   retries=MAX_VALUE     — retry indefinitely until broker confirms
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ── Email consumer ───────────────────────────────────────────────────────

    /**
     * Dedicated consumer factory for the email-service group.
     * Deserializes values to HashMap so all event types can be handled uniformly
     * without per-topic type headers.
     */
    @Bean
    public ConsumerFactory<String, Map<String, Object>> emailConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "email-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.HashMap");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Container factory for EmailConsumer:
     *  - manual ack (offsets committed only after successful DB write)
     *  - exponential backoff (1s → 4s → 16s → … ≤ 5 min) then DLQ
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> emailContainerFactory(
            ConsumerFactory<String, Map<String, Object>> emailConsumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(emailConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                (KafkaTemplate) kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));

        ExponentialBackOff backoff = new ExponentialBackOff(backoffInitialMs, backoffMultiplier);
        backoff.setMaxElapsedTime(backoffMaxElapsedMs);

        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backoff));

        return factory;
    }
}
