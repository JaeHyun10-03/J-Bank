package com.jbank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.account.domain.AccountNumberChecker;
import com.jbank.account.domain.AccountNumberGenerator;
import com.jbank.common.crypto.HmacKeyHolder;
import com.jbank.common.crypto.PiiEncryptionKeyHolder;
import java.util.Base64;
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
@Import({PiiEncryptionKeyHolder.class, HmacKeyHolder.class, AccountNumberGenerator.class})
class AccountNumberGeneratorIntegrationTest {

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

  @Autowired private AccountNumberGenerator accountNumberGenerator;

  @Test
  void 생성한_계좌번호는_체크디지트가_유효하고_지점코드로_시작한다() {
    // when
    String accountNumber = accountNumberGenerator.generate();

    // then
    assertThat(accountNumber).startsWith("110-");
    String digitsOnly = accountNumber.replace("-", "");
    assertThat(AccountNumberChecker.isValid(digitsOnly)).isTrue();
  }

  @Test
  void 연속으로_생성하면_서로_다른_계좌번호를_생성한다() {
    // when
    String first = accountNumberGenerator.generate();
    String second = accountNumberGenerator.generate();

    // then
    assertThat(first).isNotEqualTo(second);
  }
}
