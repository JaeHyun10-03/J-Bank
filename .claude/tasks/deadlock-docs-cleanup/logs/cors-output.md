# 2026-11-25 13:54 운영 CORS 확인 원래 출력(발췌)

교착 실험 작업 세션에서 실행한 `curl` 출력이다. 요청 본문은 `{}`(계정을 만들지 않음). 식별값은 없다. 헤더·본문은 앞부분만 출력했다.

```text
2026-11-25 13:54:06 KST
Origin 없음 -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED"
https://www.j-bank.site -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED" | access-control-allow-credentials: true access-control-allow-origin: https://www.j-bank.site
https://j-bank.site -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED" | access-control-allow-credentials: true access-control-allow-origin: https://j-bank.site
https://api.j-bank.site -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED" | access-control-allow-credentials: true access-control-allow-origin: https://api.j-bank.site
https://evil.example -> 403 Invalid CORS request |
https://j-bank.vercel.app -> 403 Invalid CORS request |
http://www.j-bank.site -> 403 Invalid CORS request |
proxy www customers -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED"
proxy www auth/login -> 400 {"success":false,"data":null,"error":{"code":"COMMON_001_VALIDATION_FAILED"
```
