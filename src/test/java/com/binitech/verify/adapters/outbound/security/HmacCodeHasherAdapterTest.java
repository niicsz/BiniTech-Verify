package com.binitech.verify.adapters.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacCodeHasherAdapterTest {

  private final HmacCodeHasherAdapter hasher =
      new HmacCodeHasherAdapter("pepper-de-teste-com-mais-de-32-caracteres");

  @Test
  @DisplayName("O hash não contém o código e só confere com o mesmo id e código")
  void hashesAndMatches() {
    String hash = hasher.hash("v1", "123456");

    assertThat(hash).doesNotContain("123456");
    assertThat(hasher.matches("v1", "123456", hash)).isTrue();
    assertThat(hasher.matches("v1", "123457", hash)).isFalse();
    assertThat(hasher.matches("v2", "123456", hash)).isFalse();
    assertThat(hasher.matches("v1", "123456", null)).isFalse();
  }

  @Test
  @DisplayName("Pepper curto impede a subida do serviço")
  void requiresLongPepper() {
    assertThatThrownBy(() -> new HmacCodeHasherAdapter("curto"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Código gerado tem sempre 6 dígitos")
  void generatesSixDigits() {
    SecureRandomCodeGeneratorAdapter generator = new SecureRandomCodeGeneratorAdapter();
    for (int i = 0; i < 500; i++) {
      assertThat(generator.generate()).matches("\\d{6}");
    }
  }
}
