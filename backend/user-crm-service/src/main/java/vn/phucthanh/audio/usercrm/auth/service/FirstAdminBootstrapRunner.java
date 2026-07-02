package vn.phucthanh.audio.usercrm.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.phucthanh.audio.usercrm.auth.config.SupabaseAuthProperties;

@Component
public class FirstAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminBootstrapRunner.class);

    private final AccountProvisioningService provisioningService;
    private final SupabaseAuthProperties properties;

    public FirstAdminBootstrapRunner(
            AccountProvisioningService provisioningService,
            SupabaseAuthProperties properties
    ) {
        this.provisioningService = provisioningService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        SupabaseAuthProperties.BootstrapAdmin bootstrap = properties.bootstrapAdmin();
        if (bootstrap == null || bootstrap.email() == null || bootstrap.email().isBlank()) {
            return;
        }

        AccountProvisioningService.BootstrapResult result =
                provisioningService.bootstrapFirstAdmin(
                        bootstrap.email(),
                        bootstrap.displayName()
                );
        if (result.bootstrapped()) {
            log.info(
                    "Bootstrapped first ADMIN userId={} invitationSent={}",
                    result.userId(),
                    result.invitationSent()
            );
        } else {
            log.info("ADMIN already exists; bootstrap skipped userId={}", result.userId());
        }
    }
}
