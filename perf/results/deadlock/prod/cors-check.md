# 운영 프론트 출처 CORS 확인 (REQ-10)

모든 요청은 `POST`, `-H 'Content-Type: application/json' --data '{}'`, 출처 헤더는 `-H "Origin: <출처>"`를 인자 하나로 넘겼다. `{}`는 입력 검증에서 막혀 계정을 만들지 않는다. 계정 식별값은 적지 않는다.

## 수정 전 (2026-11-23, 커밋 65d15e0 배포 상태)

| 요청 | Origin | 결과 |
| --- | --- | --- |
| `api.j-bank.site/api/v1/customers` | `https://api.j-bank.site` | 400 `COMMON_001` |
| `api.j-bank.site/api/v1/customers` | `https://www.j-bank.site` | 403 `Invalid CORS request` |
| `api.j-bank.site/api/v1/customers` | `https://j-bank.site` | 403 `Invalid CORS request` |
| `www.j-bank.site/api/proxy/api/v1/customers` | `https://www.j-bank.site` | 403 `Invalid CORS request` |
| `www.j-bank.site/api/proxy/api/v1/auth/login` | `https://www.j-bank.site` | 403 `Invalid CORS request` |

- 요청별 시각은 남기지 않았다(16시~17시 무렵). Origin 없는 요청은 400이었다.
- `https://j-bank.site` 출처의 로그인은 수정 전에 확인하지 않았다. task.md 조사 사실의 "두 출처 모두 가입·로그인 403" 중 이 부분은 확인되지 않은 서술이다.
- 조사 중 본 500은 셸에서 `-H "Origin: ..."`가 한 인자로 붙어 깨진 헤더가 나간 결과라 뺐다.

## 수정 후 (2026-11-25 13:54:06 KST, PR #19 병합 a7ce63d, backend-cd 성공 뒤)

| 요청 | Origin | 결과 |
| --- | --- | --- |
| `api.j-bank.site/api/v1/customers` | 없음 | 400 `COMMON_001_VALIDATION_FAILED` |
| 같음 | `https://www.j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED`, 이 출처의 `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials: true` |
| 같음 | `https://j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED`, 이 출처의 `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials: true` |
| 같음 | `https://api.j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED`, 이 출처의 `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials: true` |
| 같음 | `https://evil.example` | 403 `Invalid CORS request` |
| 같음 | `https://j-bank.vercel.app` | 403 `Invalid CORS request` |
| 같음 | `http://www.j-bank.site` | 403 `Invalid CORS request` |
| `www.j-bank.site/api/proxy/api/v1/customers` | `https://www.j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED` |
| `www.j-bank.site/api/proxy/api/v1/auth/login` | `https://www.j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED` |

## 사용자 화면 확인

- `https://www.j-bank.site` 가입 성공: 2026-11-25 13:54~13:59 사이 사용자가 고객 한 명을 만들었다고 알렸다(사용자 보고).
- `https://www.j-bank.site` 로그인 성공: 가입 흐름이 이어서 로그인을 부르고, 사용자는 그 화면에서 계좌 2개를 열고 두 계좌에 입금했다(15:21 DB 조회로 각 1억 원 확인). 로그인만 따로 눌러 본 기록은 없다.
- 같은 날 가입한 두 번째 고객은 500이었다. CORS가 아니라 존재하지 않는 날짜의 생년월일로 보이며 별도 작업이다(사용자가 넣은 값은 확인하지 않음).

로컬 e2e는 `http://localhost:3000` 출처라 이 결함을 재현하지 못해, 화면은 사용자가 운영에서 직접 확인한다.
