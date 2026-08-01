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

    productRepository.save(
        new Product(
            "SAV-6M-001",
            "정기적금 6개월",
            new BigDecimal("0.0250"),
            new BigDecimal("50000.00"),
            6,
            ProductStatus.ON_SALE));
    productRepository.save(
        new Product(
            "SAV-12M-001",
            "정기적금 12개월",
            new BigDecimal("0.0320"),
            new BigDecimal("100000.00"),
            12,
            ProductStatus.ON_SALE));
    productRepository.save(
        new Product(
            "SAV-24M-001",
            "정기적금 24개월",
            new BigDecimal("0.0380"),
            new BigDecimal("100000.00"),
            24,
            ProductStatus.ON_SALE));
    productRepository.save(
        new Product(
            "SAV-36M-001",
            "정기예금 36개월",
            new BigDecimal("0.0410"),
            new BigDecimal("1000000.00"),
            36,
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
