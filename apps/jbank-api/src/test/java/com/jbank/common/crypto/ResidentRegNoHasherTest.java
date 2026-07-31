package com.jbank.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResidentRegNoHasherTest {

  @BeforeAll
  static void setUpKey() throws Exception {
    HmacKeyHolder holder = new HmacKeyHolder();
    Field field = HmacKeyHolder.class.getDeclaredField("base64Key");
    field.setAccessible(true);
    field.set(holder, Base64.getEncoder().encodeToString(new byte[32]));
    Method init = HmacKeyHolder.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(holder);
  }

  @Test
  void 같은_실명번호는_같은_해시를_반환한다() {
    // given
    String residentRegNo = "900101-1234567";

    // when
    String first = ResidentRegNoHasher.hash(residentRegNo);
    String second = ResidentRegNoHasher.hash(residentRegNo);

    // then
    assertThat(first).isEqualTo(second);
  }
}
