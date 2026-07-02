package vn.phucthanh.audio.automation;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AutomationClientConfiguration {

    @Bean("catalogClient")
    RestClient catalogClient(
            RestClient.Builder builder,
            @Value("${clients.catalog}") String baseUrl
    ) {
        return client(builder, baseUrl);
    }

    @Bean("commerceClient")
    RestClient commerceClient(
            RestClient.Builder builder,
            @Value("${clients.commerce}") String baseUrl
    ) {
        return client(builder, baseUrl);
    }

    @Bean("aftersalesClient")
    RestClient aftersalesClient(
            RestClient.Builder builder,
            @Value("${clients.aftersales}") String baseUrl
    ) {
        return client(builder, baseUrl);
    }

    private RestClient client(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
