package com.jbank.common.crypto;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA {@code AttributeConverter}는 컨테이너가 아니라 JPA 프로바이더가 no-arg 생성자로 직접 만들기 때문에 스프링 빈을 주입받을 수 없다. 그래서
 * 프로퍼티로 읽은 키를 static 필드에 보관하고 컨버터가 그 값을 읽어 쓰는 방식을 쓴다.
 */
@Component
public class PiiEncryptionKeyHolder {

  private static volatile SecretKey key;

  @Value("${jbank.crypto.pii-key}")
  private String base64Key;

  @PostConstruct
  void init() {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    key = new SecretKeySpec(keyBytes, "AES");
  }

  public static SecretKey getKey() {
    if (key == null) {
      throw new IllegalStateException("PII 암호화 키가 초기화되지 않았습니다");
    }
    return key;
  }
}
