package com.jbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JbankApiApplication {

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(JbankApiApplication.class, args);
    // 배치 프로파일은 웹서버가 없어 컨텍스트가 자연 종료되지 않는다. Kafka
    // 리스너·스케줄러의 비-데몬 스레드가 잡 완료 후에도 JVM을 붙잡고 있어서,
    // CronJob 컨테이너가 절대 끝나지 않는 문제로 이어진다.
    if (context.getEnvironment().matchesProfiles("batch")) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
