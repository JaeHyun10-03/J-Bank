package com.jbank.auth.service;

/** 컨트롤러가 쿠키를 쓰는 데 필요한 토큰 원문까지 담은 내부 전송용 타입. API 응답 바디가 아니다. */
public record AuthResult<T>(T body, String accessToken, String refreshToken, String csrfToken) {}
