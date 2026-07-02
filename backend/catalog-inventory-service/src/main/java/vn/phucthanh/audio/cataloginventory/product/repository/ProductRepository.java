package vn.phucthanh.audio.cataloginventory.product.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phucthanh.audio.cataloginventory.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    @Query("""
            select p from Product p
            where (:query is null
                   or lower(p.name) like :query
                   or lower(p.sku) like :query
                   or lower(coalesce(p.brand, '')) like :query)
              and (:category is null or p.category = :category)
              and (:status is null or p.status = :status)
            """)
    Page<Product> search(
            @Param("query") String query,
            @Param("category") String category,
            @Param("status") String status,
            Pageable pageable
    );
}
