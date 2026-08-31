package com.jbank.auth.config;

import com.jbank.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** access_token 쿠키를 읽어 유효하면 SecurityContext에 인증 정보를 심는다. 인증 주체는 고객ID 문자열이다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    readCookie(request, "access_token")
        .flatMap(jwtTokenProvider::parse)
        .ifPresent(this::authenticate);
    filterChain.doFilter(request, response);
  }

  private void authenticate(Claims claims) {
    String customerId = claims.getSubject();
    var authentication =
        new UsernamePasswordAuthenticationToken(
            customerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Optional<String> readCookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return List.of(request.getCookies()).stream()
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }
}
