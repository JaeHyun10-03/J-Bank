package com.jbank.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class HmacSha256Test {

  private final SecretKey key = generateKey();

  @Test
  void 같은_입력과_키는_항상_같은_해시를_생성한다() {
    // given
    String plainText = "900101-1234567";

    // when
    String first = HmacSha256.hash(plainText, key);
    String second = HmacSha256.hash(plainText, key);

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void 다른_입력은_다른_해시를_생성한다() {
    // given & when
    String hash1 = HmacSha256.hash("900101-1234567", key);
    String hash2 = HmacSha256.hash("900101-1234568", key);

    // then
    assertThat(hash1).isNotEqualTo(hash2);
  }

  private static SecretKey generateKey() {
    try {
      KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
      return keyGenerator.generateKey();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
