package com.binitech.verify.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceKeyFilterTest {

  private static final String KEY = "chave-de-servico-com-mais-de-32-caracteres";

  private final ServiceKeyFilter filter = new ServiceKeyFilter(KEY);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private Authentication run(String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/verifications");
    if (header != null) {
      request.addHeader(ServiceKeyFilter.HEADER, header);
    }
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  @DisplayName("Chave certa autentica como serviço")
  void validKey() throws Exception {
    Authentication authentication = run(KEY);

    assertThat(authentication).isNotNull();
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_SERVICE");
  }

  @Test
  @DisplayName("Sem chave ou com chave errada não autentica")
  void invalidKey() throws Exception {
    assertThat(run(null)).isNull();
    assertThat(run(KEY + "x")).isNull();
    assertThat(run("errada")).isNull();
  }

  @Test
  @DisplayName("Chave curta impede a subida do serviço")
  void requiresLongKey() {
    assertThatThrownBy(() -> new ServiceKeyFilter("curta"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
