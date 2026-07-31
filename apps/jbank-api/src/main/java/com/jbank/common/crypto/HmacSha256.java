package com.jbank.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** 결정론적 해시. 같은 입력과 키에는 항상 같은 값을 내어 동등 비교로 조회할 수 있게 한다. */
public final class HmacSha256 {

  private static final String ALGORITHM = "HmacSHA256";

  private HmacSha256() {}

  public static String hash(String plainText, SecretKey key) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] digest = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 해시 생성에 실패했습니다", e);
    }
  }
}
