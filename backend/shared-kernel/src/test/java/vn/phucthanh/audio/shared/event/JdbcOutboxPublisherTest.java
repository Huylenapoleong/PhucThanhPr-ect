package vn.phucthanh.audio.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class JdbcOutboxPublisherTest {

    @Test
    void convertsEventInstantToJdbcSupportedOffsetDateTime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcOutboxPublisher publisher = new JdbcOutboxPublisher(jdbcTemplate, new ObjectMapper());
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);

        publisher.publish(
                "USER_ACCOUNT",
                java.util.UUID.randomUUID(),
                "user.registered.v1",
                java.util.Map.of("email", "customer@example.com")
        );

        verify(jdbcTemplate).update(anyString(), arguments.capture());
        assertThat(arguments.getValue()[7]).isInstanceOf(OffsetDateTime.class);
    }
}
