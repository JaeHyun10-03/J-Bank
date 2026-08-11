package com.jbank.product.repository;

import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.ProductContract;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductContractRepository extends JpaRepository<ProductContract, Long> {

  Page<ProductContract> findByCustomerId(Long customerId, Pageable pageable);

  // 이자 계산·만기 처리 배치가 기준일까지 만기가 도래한 활성 계약을 페이지 단위로 읽는다(구현계획 W5).
  // exclusiveUpperBound는 기준일 다음날 자정이라 기준일 당일 만기까지 포함한다.
  Page<ProductContract> findByStatusAndMaturityAtLessThan(
      ContractStatus status, OffsetDateTime exclusiveUpperBound, Pageable pageable);
}
