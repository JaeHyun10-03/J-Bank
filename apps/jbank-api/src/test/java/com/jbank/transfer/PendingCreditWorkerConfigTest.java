package com.jbank.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jbank.transfer.repository.PendingCreditRepository;
import com.jbank.transfer.service.CreditApplier;
import com.jbank.transfer.service.PendingCreditWorker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** 입금 반영 워커는 운영 api에서만 돌고 배치 JVM·기본값(테스트)에서는 돌지 않는다(ADR 0012). */
class PendingCreditWorkerConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(PendingCreditRepository.class, () -> mock(PendingCreditRepository.class))
          .withBean(CreditApplier.class, () -> mock(CreditApplier.class))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withUserConfiguration(PendingCreditWorker.class);

  @Test
  void 설정이_없으면_워커를_띄우지_않는다() {
    runner.run(context -> assertThat(context).doesNotHaveBean(PendingCreditWorker.class));
  }

  @Test
  void 켜면_워커를_띄운다() {
    runner
        .withPropertyValues("jbank.transfer.credit-worker.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(PendingCreditWorker.class));
  }

  @Test
  void 운영_설정은_켜고_배치_프로파일은_끈다() throws IOException {
    assertThat(property("application.yml")).isEqualTo(true);
    assertThat(property("application-batch.yml")).isEqualTo(false);
  }

  private Object property(String file) throws IOException {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
    return sources.get(0).getProperty("jbank.transfer.credit-worker.enabled");
  }
}
