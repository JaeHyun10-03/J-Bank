package com.jbank.auth.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** JwtAuthenticationFilter가 SecurityContext에 심어둔 인증 주체(고객ID)를 컨트롤러 파라미터로 바로 받는다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCustomerId {}
