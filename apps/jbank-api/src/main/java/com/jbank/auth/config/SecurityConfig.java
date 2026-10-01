package com.jbank.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jbank.auth.jwt.JwtTokenProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * W3 정식 인증 필터체인. JWT 쿠키 인증 + CSRF 이중제출 검증 + 세션 미사용(STATELESS)으로 구성한다(API설계 2.2·2.8절). W1의 permitAll
 * 임시 설정을 여기서 교체한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/api/v1/customers",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/actuator/health/**", // kubelet이 인증 없이 readiness/liveness probe를 호출해야 함
    "/readyz", // 본 포트의 상태 확인 경로(관리 포트를 분리해도 본 포트에 남는다)
    "/livez",
    "/api/v1/products"
  };

  private final JwtTokenProvider jwtTokenProvider;
  private final ObjectMapper objectMapper;
  private final Environment environment;

  public SecurityConfig(
      JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper, Environment environment) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.objectMapper = objectMapper;
    this.environment = environment;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()) // 이중제출 필터(CsrfDoubleSubmitFilter)로 대체한다.
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(new PrometheusScrapeRequestMatcher(environment))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                    .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new CsrfDoubleSubmitFilter(objectMapper), JwtAuthenticationFilter.class);
    return http.build();
  }

  // 로컬 개발용. 배포 환경은 프론트엔드 프록시 덕분에 브라우저 기준 동일 출처가 되어 CORS
  // 자체가 발생하지 않는다(구현계획 4절 W3).
  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
