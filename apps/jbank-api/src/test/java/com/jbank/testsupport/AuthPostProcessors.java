package com.jbank.testsupport;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * @WebMvcTest에서 SecurityConfig의 실제 필터 두 개(JWT 인증, CSRF 이중제출)를 통과시키는 공용 헬퍼. 5개 이상의 컨트롤러 테스트가 똑같은 패턴을
 * 반복해서 뺐다.
 */
public final class AuthPostProcessors {

  private static final String CSRF_TOKEN = "test-csrf-token";

  private AuthPostProcessors() {}

  public static RequestPostProcessor asCustomer(Long customerId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new UsernamePasswordAuthenticationToken(
            String.valueOf(customerId),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  /** CsrfDoubleSubmitFilter가 요구하는 쿠키·헤더 쌍을 같은 값으로 채운다. */
  public static RequestPostProcessor csrf() {
    return request -> {
      Cookie[] existing = request.getCookies();
      Cookie[] withCsrf =
          existing == null
              ? new Cookie[] {new Cookie("XSRF-TOKEN", CSRF_TOKEN)}
              : Arrays.copyOf(existing, existing.length + 1);
      if (existing != null) {
        withCsrf[existing.length] = new Cookie("XSRF-TOKEN", CSRF_TOKEN);
      }
      request.setCookies(withCsrf);
      request.addHeader("X-CSRF-TOKEN", CSRF_TOKEN);
      return request;
    };
  }
}
