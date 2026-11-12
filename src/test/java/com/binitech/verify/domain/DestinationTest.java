package com.binitech.verify.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.verify.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationTest {

  @ParameterizedTest
  @ValueSource(strings = {"(11) 98765-4321", "11987654321", "+55 11 98765-4321", "5511987654321"})
  @DisplayName("Celular em qualquer formatação vira 55 + DDD + número")
  void normalizesMobile(String raw) {
    Destination destination = Destination.of(Channel.SMS, raw);

    assertThat(destination.value()).isEqualTo("5511987654321");
    assertThat(destination.masked()).isEqualTo("(11) *****-4321");
  }

  @ParameterizedTest
  @ValueSource(strings = {"(11) 3456-7890", "1198765432", "00987654321", "11887654321", "", "abc"})
  @DisplayName("Fixo, número curto ou sem DDD é recusado")
  void rejectsInvalidMobile(String raw) {
    assertThatThrownBy(() -> Destination.of(Channel.SMS, raw))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("celular com DDD");
  }

  @Test
  @DisplayName("E-mail é guardado em minúsculas e mascarado")
  void normalizesEmail() {
    Destination destination = Destination.of(Channel.EMAIL, "  Cliente.Teste@Example.COM ");

    assertThat(destination.value()).isEqualTo("cliente.teste@example.com");
    assertThat(destination.masked()).isEqualTo("c***@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"sem-arroba.com", "a@b", "a b@c.com", "@example.com"})
  @DisplayName("E-mail inválido é recusado")
  void rejectsInvalidEmail(String raw) {
    assertThatThrownBy(() -> Destination.of(Channel.EMAIL, raw))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("Sem canal pede para escolher e-mail ou SMS")
  void requiresChannel() {
    assertThatThrownBy(() -> Destination.of(null, "a@b.com"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("e-mail ou por SMS");
  }
}
