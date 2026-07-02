package vn.phucthanh.audio.cataloginventory.product.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.phucthanh.audio.cataloginventory.product.domain.Product;
import vn.phucthanh.audio.cataloginventory.product.domain.ProductImage;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.CreateProductRequest;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.ProductImageResponse;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.ProductPageResponse;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.ProductResponse;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.UpdateCommercialDataRequest;
import vn.phucthanh.audio.cataloginventory.product.repository.ProductImageRepository;
import vn.phucthanh.audio.cataloginventory.product.repository.ProductRepository;
import vn.phucthanh.audio.shared.domain.BusinessCodes;
import vn.phucthanh.audio.shared.event.OutboxPublisher;
import vn.phucthanh.audio.shared.web.BusinessException;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StringRedisTemplate redis;
    private final OutboxPublisher outbox;
    private final String publicMediaBaseUrl;

    public ProductService(
            ProductRepository products,
            ProductImageRepository images,
            StringRedisTemplate redis,
            OutboxPublisher outbox,
            @Value("${catalog.public-media-base-url}") String publicMediaBaseUrl
    ) {
        this.products = products;
        this.images = images;
        this.redis = redis;
        this.outbox = outbox;
        this.publicMediaBaseUrl = trimTrailingSlash(publicMediaBaseUrl);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse search(
            String query,
            String category,
            String status,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        var result = products.search(
                pattern,
                blankToNull(category),
                blankToNull(status),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        Map<Long, List<ProductImage>> imagesByProduct = imagesByProduct(
                result.getContent().stream().map(Product::getId).toList()
        );
        List<ProductResponse> items = result.getContent().stream()
                .map(product -> toResponse(product, imagesByProduct.getOrDefault(product.getId(), List.of())))
                .toList();
        return new ProductPageResponse(
                items,
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        Product product = required(id);
        return toResponse(
                product,
                images.findByProductIdInAndStatusOrderBySortOrderAscIdAsc(List.of(id), "active")
        );
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku() == null || request.sku().isBlank()
                ? BusinessCodes.next("SP")
                : request.sku().trim().toUpperCase(Locale.ROOT);
        products.findBySkuIgnoreCase(sku).ifPresent(existing -> {
            throw new BusinessException(409, "SKU_EXISTS", "SKU đã tồn tại");
        });
        Product product = Product.create(
                sku,
                request.name().trim(),
                request.category(),
                blankToNull(request.brand()),
                blankToNull(request.model()),
                request.costPrice(),
                request.salePrice(),
                request.unit() == null ? "piece" : request.unit(),
                request.minimumStock(),
                request.defaultWarrantyMonths(),
                blankToNull(request.description()),
                request.specifications()
        );
        product = products.saveAndFlush(product);
        evict(product.getId());
        outbox.publish("PRODUCT", product.getId(), "product.created.v1", Map.of(
                "productId", product.getId(),
                "sku", product.getSku()
        ));
        return toResponse(product, List.of());
    }

    @Transactional
    public ProductResponse updateCommercialData(long id, UpdateCommercialDataRequest request) {
        Product product = required(id);
        if (product.getVersion() != request.version()) {
            throw new BusinessException(409, "CONCURRENT_UPDATE", "Sản phẩm vừa được cập nhật");
        }
        product.changeCommercialData(request.salePrice(), request.costPrice(), request.status());
        products.saveAndFlush(product);
        evict(id);
        outbox.publish("PRODUCT", id, "product.commercial-data-changed.v1", Map.of(
                "productId", id,
                "salePrice", request.salePrice(),
                "status", request.status()
        ));
        return toResponse(
                product,
                images.findByProductIdInAndStatusOrderBySortOrderAscIdAsc(List.of(id), "active")
        );
    }

    private Product required(long id) {
        return products.findById(id)
                .orElseThrow(() -> new BusinessException(
                        404,
                        "PRODUCT_NOT_FOUND",
                        "Không tìm thấy sản phẩm " + id
                ));
    }

    private Map<Long, List<ProductImage>> imagesByProduct(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return images.findByProductIdInAndStatusOrderBySortOrderAscIdAsc(productIds, "active")
                .stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));
    }

    private ProductResponse toResponse(Product product, List<ProductImage> productImages) {
        List<ProductImageResponse> imageResponses = productImages.stream()
                .map(this::toImage)
                .toList();
        if (imageResponses.isEmpty()
                && product.getLegacyImageUrl() != null
                && !product.getLegacyImageUrl().isBlank()) {
            imageResponses = List.of(new ProductImageResponse(
                    0,
                    product.getLegacyImageUrl(),
                    product.getName(),
                    0,
                    true
            ));
        }
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getSalePrice(),
                product.getUnit(),
                product.getStockQuantity(),
                product.getMinimumStock(),
                product.getStockStatus(),
                product.getDefaultWarrantyMonths(),
                product.getDescription(),
                product.getSpecifications(),
                product.getStatus(),
                product.getVersion(),
                imageResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private ProductImageResponse toImage(ProductImage image) {
        String url = publicMediaBaseUrl + "/" + image.getBucketName() + "/" + image.getObjectKey();
        return new ProductImageResponse(
                image.getId(),
                url,
                image.getAltText(),
                image.getSortOrder(),
                image.isPrimary()
        );
    }

    private void evict(long id) {
        try {
            redis.delete("product:" + id);
            redis.delete("products:active");
        } catch (RuntimeException exception) {
            log.warn("Redis cache eviction failed for product {}; database remains source of truth", id);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
