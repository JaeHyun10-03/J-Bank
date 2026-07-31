package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerRiskAssessmentHistory;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.repository.CustomerRiskAssessmentHistoryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class})
class CustomerRiskAssessmentHistoryRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("jbank.crypto.pii-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add("jbank.crypto.hash-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerRiskAssessmentHistoryRepository historyRepository;

  @Test
  void 고객별_위험도_판정_이력을_저장하면_고객ID로_조회된다() {
    // given
    Customer customer =
        new Customer(
            "정민성",
            "900101-1234567",
            "hash-" + System.nanoTime(),
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울특별시 강남구",
            "회사원",
            IdentityVerificationMethod.NON_FACE_TO_FACE,
            OffsetDateTime.now(),
            KycGrade.GENERAL,
            RiskLevel.LOW,
            null,
            null,
            CustomerStatus.ACTIVE);
    Long customerId = customerRepository.saveAndFlush(customer).getCustomerId();

    CustomerRiskAssessmentHistory history =
        new CustomerRiskAssessmentHistory(
            customerId, KycGrade.GENERAL, RiskLevel.LOW, null, null, "SYSTEM");

    // when
    historyRepository.saveAndFlush(history);
    List<CustomerRiskAssessmentHistory> found = historyRepository.findByCustomerId(customerId);

    // then
    assertThat(found).hasSize(1);
    assertThat(found.get(0).getKycGrade()).isEqualTo(KycGrade.GENERAL);
    assertThat(found.get(0).getAssessedBy()).isEqualTo("SYSTEM");
  }
}
