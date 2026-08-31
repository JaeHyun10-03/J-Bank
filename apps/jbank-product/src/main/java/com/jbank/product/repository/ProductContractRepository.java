package com.jbank.product.repository;

import com.jbank.product.domain.ContractStatus;
import com.jbank.product.domain.ProductContract;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductContractRepository extends JpaRepository<ProductContract, Long> {

  // 사가 진행 중(PENDING)인 행은 아직 확정 전이라 고객에게 "내 가입상품"으로 보여주지 않는다.
  Page<ProductContract> findByCustomerIdAndStatusNot(
      Long customerId, ContractStatus excludedStatus, Pageable pageable);

  // 이자 계산·만기 처리 내부 API(구현계획 W5, W7에서 jbank-api의 배치가 호출하는 쪽으로 바뀜)가
  // 기준일까지 만기가 도래한 활성 계약을 읽는다. exclusiveUpperBound는 기준일 다음날 자정이라
  // 기준일 당일 만기까지 포함한다.
  List<ProductContract> findByStatusAndMaturityAtLessThan(
      ContractStatus status, OffsetDateTime exclusiveUpperBound);
}
