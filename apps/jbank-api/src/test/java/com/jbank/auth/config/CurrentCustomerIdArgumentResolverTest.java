package com.jbank.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentCustomerIdArgumentResolverTest {

  private final CurrentCustomerIdArgumentResolver resolver =
      new CurrentCustomerIdArgumentResolver();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void CurrentCustomerId_붙은_Long_파라미터만_지원한다() throws NoSuchMethodException {
    MethodParameter annotated = parameterOf("withAnnotation", Long.class);
    MethodParameter notAnnotated = parameterOf("withoutAnnotation", Long.class);

    assertThat(resolver.supportsParameter(annotated)).isTrue();
    assertThat(resolver.supportsParameter(notAnnotated)).isFalse();
  }

  @Test
  void SecurityContext의_인증주체를_Long으로_변환한다() throws NoSuchMethodException {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "42", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

    Object result =
        resolver.resolveArgument(parameterOf("withAnnotation", Long.class), null, null, null);

    assertThat(result).isEqualTo(42L);
  }

  private static MethodParameter parameterOf(String methodName, Class<?> paramType)
      throws NoSuchMethodException {
    Method method = Sample.class.getDeclaredMethod(methodName, paramType);
    return new MethodParameter(method, 0);
  }

  @SuppressWarnings("unused")
  private static class Sample {
    void withAnnotation(@CurrentCustomerId Long customerId) {}

    void withoutAnnotation(Long customerId) {}
  }
}
