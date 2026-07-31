package com.jbank.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

  private final SecretKey key = generateKey();

  @Test
  void 암호화한_문자열을_복호화하면_원문과_같다() {
    // given
    String plainText = "900101-1234567";

    // when
    String cipherText = AesGcmCipher.encrypt(plainText, key);
    String decrypted = AesGcmCipher.decrypt(cipherText, key);

    // then
    assertThat(decrypted).isEqualTo(plainText);
  }

  @Test
  void 같은_평문도_암호화할_때마다_다른_암호문을_생성한다() {
    // given
    String plainText = "서울특별시 강남구";

    // when
    String first = AesGcmCipher.encrypt(plainText, key);
    String second = AesGcmCipher.encrypt(plainText, key);

    // then
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void 암호문이_변조되면_복호화시_예외가_발생한다() {
    // given
    String cipherText = AesGcmCipher.encrypt("010-1234-5678", key);
    String tampered = cipherText.substring(0, cipherText.length() - 4) + "abcd";

    // when & then
    assertThatThrownBy(() -> AesGcmCipher.decrypt(tampered, key))
        .isInstanceOf(IllegalStateException.class);
  }

  private static SecretKey generateKey() {
    try {
      KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
      keyGenerator.init(256);
      return keyGenerator.generateKey();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
