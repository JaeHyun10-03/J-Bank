package com.jbank.seed;

import com.jbank.account.domain.AccountType;
import com.jbank.account.dto.AccountOpenRequest;
import com.jbank.account.service.AccountService;
import com.jbank.customer.domain.IdentityVerificationMethod;
import com.jbank.customer.dto.CustomerRegisterRequest;
import com.jbank.customer.repository.CustomerRepository;
import com.jbank.customer.service.CustomerService;
import com.jbank.product.domain.Product;
import com.jbank.product.domain.ProductStatus;
import com.jbank.product.repository.ProductRepository;
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
  private final ProductRepository productRepository;

  public SeedDataRunner(
      CustomerRepository customerRepository,
      CustomerService customerService,
      AccountService accountService,
      ProductRepository productRepository) {
    this.customerRepository = customerRepository;
    this.customerService = customerService;
    this.accountService = accountService;
    this.productRepository = productRepository;
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

    // 프론트 상품 화면(피그마 시안)이 기대하는 productCode로 맞춘다. 금리는 08_앱디자인노트
    // 문서의 표시값 중 기본금리만 반영(우대금리는 단일 필드로 표현할 수 없어 제외).
    productRepository.save(
        new Product(
            "j-kids",
            "J키즈 적금",
            new BigDecimal("0.0350"),
            new BigDecimal("10000.00"),
            60,
            ProductStatus.ON_SALE));
    productRepository.save(
        new Product(
            "j-farm",
            "J팜 농장",
            new BigDecimal("0.0300"),
            new BigDecimal("10000.00"),
            12,
            ProductStatus.ON_SALE));
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
