package vn.phucthanh.audio.cataloginventory.product.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phucthanh.audio.cataloginventory.product.domain.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdInAndStatusOrderBySortOrderAscIdAsc(
            Collection<Long> productIds,
            String status
    );
}
