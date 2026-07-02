package vn.phucthanh.audio.aftersales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import vn.phucthanh.audio.shared.security.SharedServletSecurityConfiguration;
import vn.phucthanh.audio.shared.event.SharedPersistenceConfiguration;

@Import({SharedServletSecurityConfiguration.class, SharedPersistenceConfiguration.class})
@SpringBootApplication
public class AfterSalesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterSalesServiceApplication.class, args);
    }
}
