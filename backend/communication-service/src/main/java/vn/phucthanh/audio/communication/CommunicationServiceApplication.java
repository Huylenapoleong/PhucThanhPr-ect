package vn.phucthanh.audio.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import vn.phucthanh.audio.shared.security.SharedServletSecurityConfiguration;
import vn.phucthanh.audio.shared.event.SharedPersistenceConfiguration;

@Import({SharedServletSecurityConfiguration.class, SharedPersistenceConfiguration.class})
@SpringBootApplication
public class CommunicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunicationServiceApplication.class, args);
    }
}
