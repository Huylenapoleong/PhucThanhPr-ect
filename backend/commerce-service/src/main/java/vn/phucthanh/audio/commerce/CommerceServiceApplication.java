package vn.phucthanh.audio.commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import vn.phucthanh.audio.shared.security.SharedServletSecurityConfiguration;
import vn.phucthanh.audio.shared.event.SharedPersistenceConfiguration;

@Import({SharedServletSecurityConfiguration.class, SharedPersistenceConfiguration.class})
@SpringBootApplication
public class CommerceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceServiceApplication.class, args);
    }
}
