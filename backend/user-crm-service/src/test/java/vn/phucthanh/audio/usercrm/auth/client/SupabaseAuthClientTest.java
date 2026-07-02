package vn.phucthanh.audio.usercrm.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import vn.phucthanh.audio.shared.web.BusinessException;
import vn.phucthanh.audio.usercrm.auth.config.SupabaseAuthProperties;

class SupabaseAuthClientTest {

    private static final String BASE_URL = "https://project.supabase.co";

    private MockRestServiceServer server;
    private SupabaseAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SupabaseAuthClient(
                builder.build(),
                new SupabaseAuthProperties(
                        BASE_URL,
                        "publishable-key",
                        "",
                        "http://localhost:3000/auth/confirm",
                        new SupabaseAuthProperties.BootstrapAdmin("", "Administrator"),
                        new SupabaseAuthProperties.RefreshCookie("refresh", false, "Lax", 3600)
                ),
                new ObjectMapper()
        );
    }

    @Test
    void signupMapsExistingUserFromResponseBody() {
        server.expect(once(), requestTo(BASE_URL + "/auth/v1/signup?redirect_to=http://localhost:3000/auth/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": 422,
                                  "error_code": "user_already_exists",
                                  "msg": "User already registered"
                                }
                                """));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> client.signup("user@example.com", "secret123", "Test User")
        );

        assertThat(exception.status()).isEqualTo(409);
        assertThat(exception.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(exception.getMessage()).isEqualTo("Email đã được đăng ký");
        server.verify();
    }

    @Test
    void signupMapsWeakPasswordFromSupabaseHeader() {
        server.expect(once(), requestTo(BASE_URL + "/auth/v1/signup?redirect_to=http://localhost:3000/auth/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header("X-Sb-Error-Code", "weak_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": 422,
                                  "error_code": "weak_password",
                                  "msg": "Password is too weak"
                                }
                                """));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> client.signup("user@example.com", "password", "Test User")
        );

        assertThat(exception.status()).isEqualTo(422);
        assertThat(exception.code()).isEqualTo("WEAK_PASSWORD");
        assertThat(exception.getMessage()).isEqualTo("Mật khẩu chưa đáp ứng yêu cầu bảo mật");
        server.verify();
    }

    @Test
    void signupMapsConnectionFailureToBadGateway() {
        server.expect(once(), requestTo(BASE_URL + "/auth/v1/signup?redirect_to=http://localhost:3000/auth/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Connection reset")));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> client.signup("user@example.com", "secret123", "Test User")
        );

        assertThat(exception.status()).isEqualTo(502);
        assertThat(exception.code()).isEqualTo("SUPABASE_AUTH_UNAVAILABLE");
        server.verify();
    }
}
