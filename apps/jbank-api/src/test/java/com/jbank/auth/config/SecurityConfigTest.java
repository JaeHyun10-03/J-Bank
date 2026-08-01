package com.jbank.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

class SecurityConfigTest {

  @Test
  void PUBLIC_PATHS는_OpenAPI_yaml_스냅샷_경로도_허용한다() throws Exception {
    // given
    String[] patterns = publicPaths();
    PathPatternParser parser = new PathPatternParser();

    // when & then
    assertThat(matchesAny(parser, patterns, "/v3/api-docs.yaml")).isTrue();
    assertThat(matchesAny(parser, patterns, "/v3/api-docs")).isTrue();
  }

  @Test
  void PUBLIC_PATHS는_로그인과_토큰재발급_고객등록_상품목록을_포함한다() throws Exception {
    // given
    String[] patterns = publicPaths();
    PathPatternParser parser = new PathPatternParser();

    // when & then
    assertThat(matchesAny(parser, patterns, "/api/v1/auth/login")).isTrue();
    assertThat(matchesAny(parser, patterns, "/api/v1/auth/refresh")).isTrue();
    assertThat(matchesAny(parser, patterns, "/api/v1/customers")).isTrue();
    assertThat(matchesAny(parser, patterns, "/api/v1/products")).isTrue();
  }

  @Test
  void 계좌_거래_엔드포인트는_공개_경로가_아니다() throws Exception {
    // given
    String[] patterns = publicPaths();
    PathPatternParser parser = new PathPatternParser();

    // when & then
    assertThat(matchesAny(parser, patterns, "/api/v1/accounts")).isFalse();
    assertThat(matchesAny(parser, patterns, "/api/v1/transfers")).isFalse();
  }

  private static String[] publicPaths() throws Exception {
    Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
    field.setAccessible(true);
    return (String[]) field.get(null);
  }

  private static boolean matchesAny(PathPatternParser parser, String[] patterns, String path) {
    PathContainer container = PathContainer.parsePath(path);
    for (String pattern : patterns) {
      PathPattern compiled = parser.parse(pattern);
      if (compiled.matches(container)) {
        return true;
      }
    }
    return false;
  }
}
