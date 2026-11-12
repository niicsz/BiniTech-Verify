package com.binitech.verify.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServiceKeyFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Verify-Service-Key";
  private static final int MIN_KEY_LENGTH = 32;

  private final byte[] expected;

  public ServiceKeyFilter(String serviceKey) {
    if (serviceKey == null || serviceKey.length() < MIN_KEY_LENGTH) {
      throw new IllegalArgumentException("VERIFY_SERVICE_KEY deve ter ao menos 32 caracteres.");
    }
    this.expected = serviceKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(HEADER);
    if (supplied != null
        && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
    }
    chain.doFilter(request, response);
  }
}
