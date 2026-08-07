package com.jbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JbankApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(JbankApiApplication.class, args);
  }
}
