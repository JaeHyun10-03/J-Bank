package com.jbank.product.client;

import com.jbank.global.exception.ErrorCode;
import com.jbank.global.response.ApiResponse;
import com.jbank.product.domain.ProductException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 상품가입 사가의 출금·보상(입금) 단계에서 jbank-api의 내부 API를 호출하는 클라이언트.
 * product 모듈을 별도 배포 단위로 떼어내기 전에는 같은 JVM 안에서 AccountRepository를
 * 직접 썼지만, 이제는 계좌·거래·원장을 소유한 다른 서비스에 네트워크로만 접근할 수 있다.
 */
@Component
public class AccountServiceClient {

  private final RestClient restClient;

  public AccountServiceClient(
      RestClient.Builder restClientBuilder,
      @Value("${jbank.internal.account-service.base-url}") String baseUrl,
      @Value("${jbank.internal.api-key}") String internalApiKey) {
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("X-Internal-Api-Key", internalApiKey)
            .build();
  }

  /**
   * 사가 2단계. 실패하면 {@link ProductException}(PRD_003)을 던진다 — 이 시점엔 아직
   * 아무 돈도 안 움직였으니 호출부는 PENDING 계약을 그대로 삭제하면 된다(보상 불필요).
   */
  public AccountWithdrawResult withdraw(
      String accountNumber, BigDecimal amount, String idempotencyKey, Long customerId) {
    try {
      ApiResponse<InternalWithdrawResponse> response =
          restClient
              .post()
              .uri("/internal/v1/accounts/withdraw-by-number")
              .body(new InternalWithdrawRequest(accountNumber, amount, idempotencyKey, customerId))
              .retrieve()
              .body(new ParameterizedTypeReference<ApiResponse<InternalWithdrawResponse>>() {});
      InternalWithdrawResponse data = response == null ? null : response.data();
      if (data == null) {
        throw new ProductException(ErrorCode.PRD_003_SUBSCRIPTION_FAILED);
      }
      return new AccountWithdrawResult(data.accountId(), data.transactionId());
    } catch (RestClientResponseException e) {
      throw new ProductException(
          ErrorCode.PRD_003_SUBSCRIPTION_FAILED, describeUpstreamFailure(e));
    }
  }

  /**
   * 사가 3단계가 실패했을 때 부르는 보상 트랜잭션(출금 롤백). 이 호출 자체가 실패하면
   * 돈이 출금된 채로 안 돌아온 상태라 예외를 그대로 던져 호출부가 안다 — 여기서
   * 삼켜버리면 안 되는 경우다.
   */
  public void depositBack(Long accountId, BigDecimal amount, String idempotencyKey, Long customerId) {
    restClient
        .post()
        .uri("/internal/v1/accounts/{accountId}/deposit", accountId)
        .body(new InternalDepositRequest(amount, idempotencyKey, customerId))
        .retrieve()
        .toBodilessEntity();
  }

  private String describeUpstreamFailure(RestClientResponseException e) {
    try {
      ApiResponse<Void> body =
          e.getResponseBodyAs(new ParameterizedTypeReference<ApiResponse<Void>>() {});
      if (body != null && body.error() != null) {
        return body.error().message();
      }
    } catch (Exception ignored) {
      // 본문이 우리 포맷이 아니면(타임아웃 등) 아래 기본 메시지로 대체
    }
    return "출금 요청이 실패했습니다(status=" + e.getStatusCode() + ")";
  }

  private record InternalWithdrawRequest(
      String accountNumber, BigDecimal amount, String idempotencyKey, Long customerId) {}

  private record InternalWithdrawResponse(
      Long accountId, String transactionId, BigDecimal balanceAfter) {}

  private record InternalDepositRequest(BigDecimal amount, String idempotencyKey, Long customerId) {}
}
