package com.jbank.product.repository;

import com.jbank.product.domain.ProductContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductContractRepository extends JpaRepository<ProductContract, Long> {

  Page<ProductContract> findByCustomerId(Long customerId, Pageable pageable);
}
