package vn.phucthanh.audio.automation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import vn.phucthanh.audio.shared.security.SharedServletSecurityConfiguration;

@Import(SharedServletSecurityConfiguration.class)
@SpringBootApplication
public class AutomationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationServiceApplication.class, args);
    }
}
