package com.jbank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JacksonConfigTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JacksonConfig().bigDecimalModule());

  @Test
  void BigDecimal은_지수표기_없이_문자열로_직렬화된다() throws Exception {
    // given
    BigDecimal amount = new BigDecimal("100000000.00");

    // when
    String json = objectMapper.writeValueAsString(amount);

    // then
    assertThat(json).isEqualTo("\"100000000.00\"");
  }
}
