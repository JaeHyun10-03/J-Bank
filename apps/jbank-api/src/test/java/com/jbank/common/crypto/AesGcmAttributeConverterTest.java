package com.jbank.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AesGcmAttributeConverterTest {

  private final AesGcmAttributeConverter converter = new AesGcmAttributeConverter();

  @BeforeAll
  static void setUpKey() throws Exception {
    PiiEncryptionKeyHolder holder = new PiiEncryptionKeyHolder();
    Field field = PiiEncryptionKeyHolder.class.getDeclaredField("base64Key");
    field.setAccessible(true);
    field.set(holder, Base64.getEncoder().encodeToString(new byte[32]));
    Method init = PiiEncryptionKeyHolder.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(holder);
  }

  @Test
  void null값은_암호화하지_않고_그대로_반환한다() {
    // given & when & then
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void 저장할_때_암호화하고_읽을_때_복호화한다() {
    // given
    String plainText = "010-9876-5432";

    // when
    String encrypted = converter.convertToDatabaseColumn(plainText);
    String decrypted = converter.convertToEntityAttribute(encrypted);

    // then
    assertThat(encrypted).isNotEqualTo(plainText);
    assertThat(decrypted).isEqualTo(plainText);
  }
}
