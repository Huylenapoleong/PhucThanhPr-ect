package vn.phucthanh.audio.cataloginventory.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            String sku,
            @NotBlank String name,
            @NotBlank
            @Pattern(regexp = "audio|tv|led|av|elv|ict|accessory")
            String category,
            String brand,
            String model,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            @NotNull @DecimalMin("0") BigDecimal salePrice,
            @Pattern(regexp = "piece|set|box|meter") String unit,
            @NotNull @DecimalMin("0") BigDecimal minimumStock,
            @Min(0) @Max(240) short defaultWarrantyMonths,
            String description,
            Map<String, Object> specifications
    ) {
    }

    public record UpdateCommercialDataRequest(
            @NotNull @DecimalMin("0") BigDecimal salePrice,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            @NotBlank
            @Pattern(regexp = "active|inactive|discontinued")
            String status,
            @PositiveOrZero long version
    ) {
    }

    public record ProductImageResponse(
            long id,
            String url,
            String altText,
            int sortOrder,
            boolean primary
    ) {
    }

    public record ProductResponse(
            long id,
            String sku,
            String name,
            String category,
            String brand,
            String model,
            BigDecimal salePrice,
            String unit,
            BigDecimal stockQuantity,
            BigDecimal minimumStock,
            String stockStatus,
            short defaultWarrantyMonths,
            String description,
            Map<String, Object> specifications,
            String status,
            long version,
            List<ProductImageResponse> images,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ProductPageResponse(
            List<ProductResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
