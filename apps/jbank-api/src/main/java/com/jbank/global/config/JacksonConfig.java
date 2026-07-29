package com.jbank.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  @Bean
  public SimpleModule bigDecimalModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(BigDecimal.class, new BigDecimalStringSerializer());
    return module;
  }

  private static class BigDecimalStringSerializer extends StdSerializer<BigDecimal> {

    BigDecimalStringSerializer() {
      super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeString(value.toPlainString());
    }
  }
}
