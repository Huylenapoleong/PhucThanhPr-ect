package vn.phucthanh.audio.cataloginventory.product.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.CreateProductRequest;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.ProductPageResponse;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.ProductResponse;
import vn.phucthanh.audio.cataloginventory.product.dto.ProductDtos.UpdateCommercialDataRequest;
import vn.phucthanh.audio.cataloginventory.product.service.ProductService;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    ProductPageResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(query, category, status, page, size);
    }

    @GetMapping("/{id}")
    ProductResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/commercial-data")
    @PreAuthorize("hasRole('ADMIN')")
    ProductResponse updateCommercialData(
            @PathVariable long id,
            @Valid @RequestBody UpdateCommercialDataRequest request
    ) {
        return service.updateCommercialData(id, request);
    }
}
