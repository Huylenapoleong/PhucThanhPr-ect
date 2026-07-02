package vn.phucthanh.audio.cataloginventory.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void createInitializesCatalogDefaultsAndCanChangeCommercialData() {
        Product product = Product.create(
                "SP-001",
                "Loa kiểm âm",
                "audio",
                "Brand",
                "Model",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_500_000),
                "piece",
                BigDecimal.valueOf(2),
                (short) 12,
                "Mô tả",
                Map.of("power", "100W")
        );

        assertThat(product.getStockQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.getStatus()).isEqualTo("active");
        assertThat(product.getSpecifications()).containsEntry("power", "100W");

        product.changeCommercialData(
                BigDecimal.valueOf(1_600_000),
                BigDecimal.valueOf(1_100_000),
                "inactive"
        );

        assertThat(product.getSalePrice()).isEqualByComparingTo("1600000");
        assertThat(product.getStatus()).isEqualTo("inactive");
    }
}
