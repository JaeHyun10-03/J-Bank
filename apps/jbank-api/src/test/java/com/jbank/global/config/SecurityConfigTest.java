package com.jbank.global.config;

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
