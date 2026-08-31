package com.jbank.product.repository;

import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

  Page<Product> findByStatus(ProductStatus status, Pageable pageable);

  Optional<Product> findByProductCode(String productCode);
}
