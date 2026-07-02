package vn.phucthanh.audio.cataloginventory.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products", schema = "public")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String brand;
    private String model;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column(name = "sale_price", nullable = false)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private String unit;

    @Column(name = "stock_quantity", nullable = false)
    private BigDecimal stockQuantity;

    @Column(name = "minimum_stock", nullable = false)
    private BigDecimal minimumStock;

    @Column(name = "default_warranty_months", nullable = false)
    private short defaultWarrantyMonths;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> specifications;

    @Column(name = "image_url")
    private String legacyImageUrl;

    @Column(nullable = false)
    private String status;

    @Column(name = "stock_status", insertable = false, updatable = false)
    private String stockStatus;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public static Product create(
            String sku,
            String name,
            String category,
            String brand,
            String model,
            BigDecimal costPrice,
            BigDecimal salePrice,
            String unit,
            BigDecimal minimumStock,
            short warrantyMonths,
            String description,
            Map<String, Object> specifications
    ) {
        Product product = new Product();
        product.sku = sku;
        product.name = name;
        product.category = category;
        product.brand = brand;
        product.model = model;
        product.costPrice = costPrice;
        product.salePrice = salePrice;
        product.unit = unit;
        product.stockQuantity = BigDecimal.ZERO;
        product.minimumStock = minimumStock;
        product.defaultWarrantyMonths = warrantyMonths;
        product.description = description;
        product.specifications = specifications == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(specifications);
        product.status = "active";
        return product;
    }

    public void changeCommercialData(BigDecimal salePrice, BigDecimal costPrice, String status) {
        this.salePrice = salePrice;
        this.costPrice = costPrice;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public String getUnit() { return unit; }
    public BigDecimal getStockQuantity() { return stockQuantity; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public short getDefaultWarrantyMonths() { return defaultWarrantyMonths; }
    public String getDescription() { return description; }
    public Map<String, Object> getSpecifications() { return Map.copyOf(specifications); }
    public String getLegacyImageUrl() { return legacyImageUrl; }
    public String getStatus() { return status; }
    public String getStockStatus() { return stockStatus; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
