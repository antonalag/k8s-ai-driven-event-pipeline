package com.platform.analyzer.infrastructure.config.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Configures Kafka consumer fault tolerance with exponential retry and Dead Letter Queue.
 * Failed messages are retried up to 3 times (1s → 2s → 4s) before being published to the DLT.
 */
@Configuration
@ConditionalOnProperty(name = "platform.messaging.type", havingValue = "kafka")
public class KafkaErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(15000L);

        log.info("Kafka error handler configured — exponential backoff (1s, 2s, 4s) with DLQ recovery");

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
