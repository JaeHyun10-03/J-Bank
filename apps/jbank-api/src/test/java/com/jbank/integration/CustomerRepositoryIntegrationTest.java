package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import com.jbank.customer.domain.Customer;
import com.jbank.customer.domain.CustomerStatus;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.domain.KycGrade;
import com.jbank.customer.domain.RiskLevel;
import com.jbank.customer.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class})
class CustomerRepositoryIntegrationTest {

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

  @Test
  void 저장한_고객을_다시_조회하면_암호화됐던_평문값이_그대로_복원된다() {
    // given
    Customer customer = newCustomer("정민성", "900101-1234567", "hash-1");

    // when
    Long savedId = customerRepository.saveAndFlush(customer).getCustomerId();
    customerRepository.flush();
    Customer found = customerRepository.findById(savedId).orElseThrow();

    // then
    assertThat(found.getName()).isEqualTo("정민성");
    assertThat(found.getResidentRegNo()).isEqualTo("900101-1234567");
    assertThat(found.getPhone()).isEqualTo("010-1234-5678");
  }

  @Test
  void 같은_실명번호_해시로_두_번_저장하면_유니크_제약을_위반한다() {
    // given
    customerRepository.saveAndFlush(newCustomer("정민성", "900101-1234567", "dup-hash"));

    // when & then
    assertThatThrownBy(
            () ->
                customerRepository.saveAndFlush(newCustomer("다른사람", "900101-7654321", "dup-hash")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static Customer newCustomer(String name, String residentRegNo, String residentRegNoHash) {
    return new Customer(
        name,
        "user-" + UUID.randomUUID(),
        "pwhash-" + UUID.randomUUID(),
        residentRegNo,
        residentRegNoHash,
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
  }
}
