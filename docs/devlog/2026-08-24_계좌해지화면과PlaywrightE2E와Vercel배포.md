# 2026-08-24 계좌해지 화면, Playwright E2E, Vercel 배포 연결

같은 날 세션의 후반부. W6 프론트엔드 남은 항목(Playwright E2E,
Vercel 배포·루트 디렉터리 설정)을 마무리했다.

## 1. E2E 작성 전 발견한 갭 — 계좌해지 화면 미구현

Playwright로 04번 화면플로우차트 8절(계좌해지)을 검증하려고 보니
`app/accounts/[accountId]/close/page.tsx`가 아직 `ScreenPlaceholder`
였고, 계좌상세 화면에는 입금/출금/해지로 가는 버튼 자체가 없어서
URL 직접 이동으로만 접근 가능한 고아 라우트였다(이체만 홈 화면에서
진입 가능). 사용자 확인 후 두 화면을 다 고치고 나서 E2E를 썼다.

- 계좌해지 화면: 확인 다이얼로그(`BottomSheet` 재사용) → `DELETE
  /accounts/{id}` → 해지완료 화면. `ACC_008_BALANCE_NOT_ZERO`/
  `ACC_010_HOLD_AMOUNT_REMAINS` 도메인 에러 매핑 추가.
- 계좌상세 화면에 입금/출금/이체/해지 버튼 4개 추가.

## 2. Playwright E2E — 6개 spec, 13개 테스트

04번 문서 3~8절(앱 진입·인증, 회원가입·계좌개설, 입출금, 계좌이체,
상품가입, 계좌해지)을 실제 브라우저로 검증. 로컬 postgres/redis
도커컴포즈 + 백엔드(`local,seed`) + 프론트 dev 서버를 띄우고 실행,
전부 통과 확인.

막힌 지점과 해결:

- **DB 볼륨에 이전 세션 시드가 남아있었다.** SeedDataRunner는
  `customerRepository.count() > 0`이면 아무것도 안 해서, 오늘 아침에
  바꾼 새 시드(j-kids/j-farm, kim01/lee01)가 반영이 안 됐다. 볼륨
  삭제(`docker compose down -v`) 후 재기동해서 해결.
- **OTP 코드를 Redis에서 직접 못 읽는다.** Redisson의 `RBucket`이
  값을 바이너리 코덱으로 저장해서 `ioredis`로 `GET`하면 깨진 값이
  나온다(`89418<binary>`처럼). 실제 설계대로 "SMS 대신 로그로 나가는"
  방식을 그대로 써서, 백엔드 로그 파일(`OTP 발급:
  transactionId={}, code={}`)을 파싱하는 걸로 바꿨다. Redis는 키
  강제 삭제(OTP 만료 시뮬레이션)에만 남겨뒀다 — 이건 값 해석이
  필요 없어서 코덱 문제와 무관하다.
- **OTP 만료 처리 UI는 메시지가 뜨자마자 바로 페이지를 이동한다**
  (`otp/page.tsx`의 `backToTransferInput`). 메시지를 기다리는 assert는
  타이밍에 따라 놓칠 수 있어서, 최종적으로 이체입력 화면에
  돌아왔는지만 검증하도록 바꿨다.
- **jest가 `e2e/*.spec.ts`까지 테스트로 잡아서 충돌났다.**
  `testPathIgnorePatterns`에 `<rootDir>/e2e/`를 넣었는데도 안
  먹혔는데, 알고 보니 이 저장소 경로에 들어있는 `[02]`가 정규식
  문자클래스로 해석돼 패턴 전체가 깨지는 거였다. `<rootDir>` 절대경로
  대신 부분 문자열(`/e2e/`)로 바꿔서 해결 — 이 문제는 앞으로 이
  저장소에서 절대경로 기반 정규식 옵션을 쓸 때마다 반복될 수 있다.

다루지 않은 것: PRD_001(최소가입금액 미달)·PRD_002(판매중지)·
TXN_001(출금초과)은 화면이 클라이언트 사이드에서 버튼을 미리
비활성화해버려서 서버 에러 응답을 화면으로 재현할 수 없었다. 회원가입
EDD 분기도 KYC 등급 판정 조건을 인위적으로 만들기 어려워 제외했다.

## 3. Vercel 배포 연결

`vercel login`(대화형이라 사용자가 직접), `vercel link`로 프로젝트
연결까지는 CLI로 됐는데, `vercel git connect`가 두 가지 문제로
막혔다.

- 프로젝트 서브디렉토리(`apps/frontend`)에서 실행하면 "No local Git
  repository found" — 저장소 루트에서 실행해야 git 탐색이 된다(모노레포
  특성으로 보임).
- 저장소 루트에서 실행해도 실제 GitHub 연결 자체가
  `Failed to connect ... Make sure you have access`로 계속
  실패 — Vercel의 GitHub App이 이 저장소에 설치·권한 부여가 안 된
  상태였다. 이건 브라우저에서 GitHub App 권한을 승인해야 하는
  작업이라 CLI로 우회가 안 됐고, 결국 사용자가 Vercel 대시보드에서
  직접 Import Git Repository로 연결했다.

이 과정에서 실수로 루트 디렉터리 기준 빈 프로젝트("j-bank",
프레임워크 미감지)가 하나 더 생겨서 삭제했다. 최종적으로 남은
프로젝트 이름도 "j-bank"(사용자가 대시보드에서 새로 만든 것)라서,
로컬 `.vercel` 링크를 여기로 재연결하고 Vercel REST API로(CLI에
없는 옵션이라) `rootDirectory: apps/frontend`를 설정했다.
`ignoreCommand`(빌드 스킵 조건)는 `apps/frontend/vercel.json`에
커밋했다.

커스텀 도메인 `www.j-bank.site`(가비아 구매)도 API로 프로젝트에
추가하고, 가비아 DNS에 CNAME(`www` → `cname.vercel-dns.com`) 등록
안내 후 전파 확인했다. HTTP는 정상 응답(307 → `/welcome`)까지
확인했고, HTTPS는 인증서 자동 발급 대기 중이라 세션 종료 시점엔
아직 미확인 상태로 남았다.

## 커밋

1. `feat(frontend)`: 계좌해지 도메인 에러 매핑
2. `feat(frontend)`: 계좌해지 화면 구현
3. `fix(frontend)`: 계좌상세 화면에 입금/출금/이체/해지 버튼 추가
4. `fix(frontend)`: jest가 e2e 디렉토리를 테스트로 안 잡게 제외
5. `chore(frontend)`: Playwright 실행용 ioredis 설치, npm 스크립트
6. `test(frontend)`: Playwright E2E 6개 spec
7. `docs(todo)`: E2E 완료 체크
8. `chore(frontend)`: vercel link가 추가한 .env* gitignore
9. `chore`: 루트에서 vercel CLI 실행 시 생기는 산출물 gitignore
10. `feat(frontend)`: Vercel ignoreCommand 설정
11. `docs(todo)`: Vercel 배포·루트 디렉터리 완료 체크

## 다음

- HTTPS 인증서 발급 확인(재부팅 후에도 안 되면 Vercel 대시보드에서
  도메인 상태 직접 확인 필요).
- 백엔드(AWS ALB)에 커스텀 도메인(예 `api.j-bank.site`) 연결은
  이번 스코프 밖으로 남김 — 지금 인프라 자체가 검증 후 destroy된
  상태고, 브라우저는 프론트 same-site 프록시만 거쳐 ALB DNS
  기본값으로도 동작하므로 급하지 않다. ALB HTTPS 리스너 추가(기존
  ponytail 주석)와 묶어서 나중에 처리.
- J키즈 적금 가입 플로우 미연결(전날 devlog에 이어 계속 남은 항목).
