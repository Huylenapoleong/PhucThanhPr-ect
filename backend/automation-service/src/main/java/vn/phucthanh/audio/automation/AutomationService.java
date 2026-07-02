package vn.phucthanh.audio.automation;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class AutomationService {

    private final RestClient catalog;
    private final RestClient commerce;
    private final RestClient aftersales;

    public AutomationService(
            @Qualifier("catalogClient") RestClient catalog,
            @Qualifier("commerceClient") RestClient commerce,
            @Qualifier("aftersalesClient") RestClient aftersales
    ) {
        this.catalog = catalog;
        this.commerce = commerce;
        this.aftersales = aftersales;
    }

    public List<Map<String, Object>> matchProducts(String authorization, String query, int size) {
        try {
            return catalog.get()
                    .uri(uri -> uri.path("/products")
                            .queryParam("query", query)
                            .queryParam("status", "active")
                            .queryParam("size", Math.max(1, Math.min(size, 20)))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw unavailable("Catalog/Inventory", exception);
        }
    }

    public Map<String, Object> createLead(String authorization, Map<String, Object> payload) {
        try {
            return commerce.post()
                    .uri("/leads")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw unavailable("Commerce", exception);
        }
    }

    public Map<String, Object> createRepair(String authorization, Map<String, Object> payload) {
        try {
            return aftersales.post()
                    .uri("/repairs")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw unavailable("After-sales", exception);
        }
    }

    private BusinessException unavailable(String service, Exception cause) {
        return new BusinessException(
                503,
                "UPSTREAM_UNAVAILABLE",
                service + " Service hiện không phản hồi"
        );
    }
}
