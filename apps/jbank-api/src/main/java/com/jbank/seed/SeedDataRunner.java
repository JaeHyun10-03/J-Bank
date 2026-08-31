package com.jbank.seed;

import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.service.AccountService;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.service.CustomerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 시연용 시드 데이터. `--spring.profiles.active=local,seed`로만 켜지고, 데이터가 이미 있으면 아무것도 하지 않는다. */
@Profile("seed")
@Component
public class SeedDataRunner implements ApplicationRunner {

  private final CustomerRepository customerRepository;
  private final CustomerService customerService;
  private final AccountService accountService;

  public SeedDataRunner(
      CustomerRepository customerRepository,
      CustomerService customerService,
      AccountService accountService) {
    this.customerRepository = customerRepository;
    this.customerService = customerService;
    this.accountService = accountService;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (customerRepository.count() > 0) {
      return;
    }

    Long customer1 = registerCustomer("kim01", "9001011234567", "김민준", "010-1000-0001");
    Long customer2 = registerCustomer("lee01", "9202022345678", "이서연", "010-1000-0002");

    accountService.open(new AccountOpenRequest(AccountType.CHECKING, BigDecimal.ZERO), customer1);
    accountService.open(new AccountOpenRequest(AccountType.CHECKING, BigDecimal.ZERO), customer2);

    // 상품 시드는 jbank-product의 SeedDataRunner로 옮겼다(W7 모듈 분리) — 상품은 이제
    // 이 서비스가 소유하지 않는다.
  }

  private Long registerCustomer(String loginId, String residentRegNo, String name, String phone) {
    var response =
        customerService.register(
            new CustomerRegisterRequest(
                name,
                loginId,
                "seed-password-1234",
                residentRegNo,
                LocalDate.of(1992, 3, 4),
                phone,
                "서울특별시 강남구",
                "회사원",
                IdentityVerificationMethod.FACE_TO_FACE,
                "생활비 관리",
                "근로소득"));
    return Long.valueOf(response.customerId());
  }
}
