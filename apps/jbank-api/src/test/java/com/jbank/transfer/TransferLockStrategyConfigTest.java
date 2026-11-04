package com.jbank.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbank.transfer.service.TransferLockStrategy;
import com.jbank.transfer.service.TransferLockStrategy.Mode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** 이체 락 전략 설정: 기본 current, 잘못된 값이면 기동 실패, 기동 로그(교착 재현 실험). */
@ExtendWith(OutputCaptureExtension.class)
class TransferLockStrategyConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TransferLockStrategy.class);

  @Test
  void 설정이_없으면_current이고_INFO만_한_번_남긴다(CapturedOutput output) {
    runner.run(
        context -> {
          assertThat(context.getBean(TransferLockStrategy.class).mode()).isEqualTo(Mode.CURRENT);
          assertThat(count(output, "이체 락 전략: current")).isEqualTo(1);
          assertThat(count(output, "실험용")).isZero();
        });
  }

  @Test
  void 빈_값은_current다() {
    runner
        .withPropertyValues("jbank.transfer.lock-strategy=  ")
        .run(
            context ->
                assertThat(context.getBean(TransferLockStrategy.class).mode())
                    .isEqualTo(Mode.CURRENT));
  }

  @Test
  void current가_아니면_전략_이름을_담은_WARN을_한_번_남긴다(CapturedOutput output) {
    runner
        .withPropertyValues("jbank.transfer.lock-strategy=sorted-for-update")
        .run(
            context -> {
              assertThat(context.getBean(TransferLockStrategy.class).mode())
                  .isEqualTo(Mode.SORTED_FOR_UPDATE);
              assertThat(count(output, "이체 락 전략: sorted-for-update")).isEqualTo(1);
              assertThat(count(output, "실험용 sorted-for-update")).isEqualTo(1);
            });
  }

  @Test
  void 잘못된_값이면_기동하지_않는다() {
    runner
        .withPropertyValues("jbank.transfer.lock-strategy=unsorted")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("unsorted")
                  .hasMessageContaining("current, sorted-for-update, unsorted-for-update");
            });
  }

  @Test
  void 환경변수로_바뀐다() {
    runner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            Map.of("JBANK_TRANSFER_LOCK_STRATEGY", "unsorted-for-update"))))
        .run(
            context ->
                assertThat(context.getBean(TransferLockStrategy.class).mode())
                    .isEqualTo(Mode.UNSORTED_FOR_UPDATE));
  }

  private static int count(CapturedOutput output, String text) {
    return output.getAll().split(text, -1).length - 1;
  }
}
