package vn.phucthanh.audio.shared.event;

import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class SharedPersistenceConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcOutboxPublisher(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
    OutboxRelay outboxRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.relay.aggregate-types:}") String aggregateTypes,
            @Value("${outbox.relay.batch-size:100}") int batchSize,
            @Value("${outbox.relay.publish-timeout:5s}") Duration publishTimeout
    ) {
        return new OutboxRelay(
                jdbcTemplate,
                kafkaTemplate,
                aggregateTypes,
                batchSize,
                publishTimeout
        );
    }
}
