package vn.phucthanh.audio.usercrm.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import vn.phucthanh.audio.shared.web.BusinessException;
import vn.phucthanh.audio.usercrm.auth.config.SupabaseAuthProperties;
import vn.phucthanh.audio.usercrm.auth.dto.AuthDtos.SupabaseUser;

class SupabaseAdminAuthClientTest {

    private static final String BASE_URL = "https://project.supabase.co";

    private MockRestServiceServer server;
    private SupabaseAdminAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SupabaseAdminAuthClient(
                builder.build(),
                properties("secret-key"),
                new ObjectMapper()
        );
    }

    @Test
    void inviteUserUsesServerSecretAndReturnsUser() {
        server.expect(
                        once(),
                        requestTo(BASE_URL
                                + "/auth/v1/invite?redirect_to=http://localhost:3000/auth/confirm")
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "secret-key"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "6e46e506-9d0b-4c16-adb0-6a7e498454aa",
                          "email": "sales@example.com",
                          "user_metadata": {"display_name": "Sales A"},
                          "app_metadata": {}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        SupabaseUser user = client.inviteUser("sales@example.com", "Sales A");

        assertThat(user.id().toString()).isEqualTo("6e46e506-9d0b-4c16-adb0-6a7e498454aa");
        assertThat(user.email()).isEqualTo("sales@example.com");
        server.verify();
    }

    @Test
    void inviteUserRequiresBackendSecret() {
        SupabaseAdminAuthClient unconfigured = new SupabaseAdminAuthClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                properties(""),
                new ObjectMapper()
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> unconfigured.inviteUser("sales@example.com", "Sales A")
        );

        assertThat(exception.status()).isEqualTo(503);
        assertThat(exception.code()).isEqualTo("SUPABASE_ADMIN_NOT_CONFIGURED");
    }

    private SupabaseAuthProperties properties(String secretKey) {
        return new SupabaseAuthProperties(
                BASE_URL,
                "publishable-key",
                secretKey,
                "http://localhost:3000/auth/confirm",
                new SupabaseAuthProperties.BootstrapAdmin("", "Administrator"),
                new SupabaseAuthProperties.RefreshCookie("refresh", false, "Lax", 3600)
        );
    }
}
