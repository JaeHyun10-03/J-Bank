# J-Bank 코어시스템 화면 플로우차트

## 버전 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| v1.0 | 2026-07-21 | 최초 작성 - Phase 1+2 고객용 화면 전체에 대한 화면 전환 플로우차트 작성 |
| v1.1 | 2026-07-26 | 프로젝트명을 J-Bank로 변경. 인증 플로우의 토큰 저장 노드를 쿠키 기반으로 수정, 출금 가능 금액과 지급정지 개념을 입출금·이체 플로우에 반영 |

## 관련 문서

- J-Bank_요구사항명세서.md
- J-Bank_API설계.md
- J-Bank_프론트엔드기술스택.md

---

## 1. 문서 개요

이 문서는 프론트엔드 화면 설계와 라우팅 구조를 잡기 위한 화면 전환 플로우차트다. API설계.md가 서버 관점에서 "어떤 엔드포인트가 무엇을 주고받는가"를 다뤘다면, 이 문서는 클라이언트 관점에서 "사용자가 어떤 화면에서 어떤 행동을 하면 다음에 어떤 화면으로 가는가"를 다룬다.

범위는 Phase 1과 Phase 2에 해당하는 고객용 화면이다. 운영자 전용 화면(감사로그 조회, CTR 대상 조회, 이상거래 조회)은 별도의 관리자 콘솔 성격이 강해 이번 범위에서 제외했으며, 필요해지면 별도 문서로 다룬다.

각 다이어그램의 사각형 노드는 화면, 마름모 노드는 서버 응답이나 입력값에 따른 분기, 화살표 라벨은 사용자 행동 또는 API 처리 결과를 의미한다. 각 분기에 표기된 코드(예: `TXN_001`)는 API설계.md에 정의된 에러코드와 동일하므로, 프론트엔드에서 에러 핸들링 로직을 짤 때 이 문서와 API설계.md를 함께 참조하면 된다.

## 2. 전체 화면 구조도

전체 화면이 어떻게 연결되는지 한눈에 보기 위한 사이트맵이다. 세부 분기는 3절 이후 개별 플로우에서 다룬다.

```mermaid
flowchart LR
    Login["로그인 화면"]
    Signup["회원가입 - 정보입력"]
    EDD["강화된 고객확인 입력"]
    AccountOpen["계좌개설"]
    Home["홈 - 계좌목록"]
    AccountDetail["계좌상세"]
    TxHistory["거래내역 조회"]
    Deposit["입금"]
    Withdraw["출금"]
    TransferInput["이체 - 입력"]
    TransferConfirm["이체 - 확인"]
    Otp["OTP 검증"]
    TransferComplete["이체 완료"]
    ProductList["상품목록"]
    ProductDetail["상품상세"]
    ProductSubscribe["상품가입 신청"]
    MyContracts["내 가입상품"]
    AccountClose["계좌해지"]

    Login --> Home
    Login --> Signup
    Signup --> EDD
    Signup --> AccountOpen
    EDD --> AccountOpen
    AccountOpen --> Home
    Home --> AccountDetail
    Home --> ProductList
    Home --> MyContracts
    AccountDetail --> TxHistory
    AccountDetail --> Deposit
    AccountDetail --> Withdraw
    AccountDetail --> TransferInput
    AccountDetail --> AccountClose
    TransferInput --> TransferConfirm
    TransferConfirm --> Otp
    TransferConfirm --> TransferComplete
    Otp --> TransferComplete
    ProductList --> ProductDetail
    ProductDetail --> ProductSubscribe
    ProductSubscribe --> MyContracts
```

## 3. 앱 진입 및 인증 플로우

앱 실행 시점부터 로그인, 토큰 재발급, 로그아웃까지의 흐름이다. 인증 쿠키의 유효성에 따라 홈으로 바로 진입할지, 조용히 재발급을 시도할지, 로그인 화면으로 보낼지가 갈린다.

```mermaid
flowchart TD
    Start(["앱 실행"]) --> CheckCookie{"인증 쿠키 있음?"}
    CheckCookie -- 없음 --> Login["로그인 화면"]
    CheckCookie -- 있음, 유효 --> Home["홈 화면"]
    CheckCookie -- 있음, 만료 --> Refresh["API-014 토큰 재발급 시도"]
    Refresh --> RefreshOk{"재발급 성공?"}
    RefreshOk -- 성공 --> Home
    RefreshOk -- 실패, 갱신 쿠키도 만료/폐기 --> Login

    Login --> InputCred["아이디/비밀번호 입력"]
    InputCred --> SubmitLogin["API-011 로그인 요청"]
    SubmitLogin --> LoginResult{"인증 결과"}
    LoginResult -- 성공 --> SetCookie["서버가 인증·갱신·위조방지 쿠키 3종 발급"] --> Home
    LoginResult -- "AUTH_001 자격증명 불일치" --> LoginError["에러 메시지 표시, 실패 카운트 증가"]
    LoginError --> LockCheck{"5회 연속 실패?"}
    LockCheck -- 아니오 --> Login
    LockCheck -- 예 --> Locked["계정 잠김 안내 화면"]
    LoginResult -- "AUTH_002 계정 잠김" --> Locked

    Home --> Logout["로그아웃 버튼"]
    Logout --> SubmitLogout["API-015 로그아웃 요청"] --> ClearCookie["서버가 쿠키 3종 만료 처리"] --> Login
```

프론트엔드 구현 시 참고할 점이 세 가지다.

첫째, 토큰 재발급은 사용자에게 별도 화면을 보여주지 않고 백그라운드에서 조용히 처리해야 한다. 인증 쿠키 만료로 API 호출이 401을 반환하면, 그 시점에 API-014를 자동 호출하고 원래 요청을 재시도하는 인터셉터 패턴으로 구현하는 것이 일반적이다.

둘째, 클라이언트가 토큰 값을 저장하는 단계가 없다는 점이다. 서버가 `Set-Cookie`로 내려주고 브라우저가 보관하며, 인증 쿠키와 갱신 쿠키는 스크립트가 읽을 수 없다. 로그아웃도 로컬 저장소를 비우는 것이 아니라 서버가 쿠키를 만료시키는 방식이다.

셋째, 동시에 여러 요청이 401을 받는 경우를 처리해야 한다. 각 요청이 독립적으로 재발급을 시도하면 갱신 토큰 로테이션 때문에 두 번째 요청 이후가 이미 사용된 토큰을 제출하게 되고, 서버가 이를 탈취로 판단해 토큰 계열 전체를 폐기한다. 재발급 요청을 하나로 합치고 나머지는 그 결과를 기다리게 해야 한다.

## 4. 회원가입 및 계좌개설 플로우

신규 고객이 정보를 입력하는 시점부터 CDD, 필요 시 EDD, 계좌개설까지 이어지는 흐름이다. `eddRequired` 값에 따라 분기하는 지점이 프론트엔드 라우팅에서 가장 신경 써야 할 부분이다.

```mermaid
flowchart TD
    Login["로그인 화면"] --> SignupBtn["회원가입 버튼"]
    SignupBtn --> SignupForm["회원정보 입력 화면 - 이름/실명번호/생년월일/연락처/주소/직업/거래목적/자금원천"]
    SignupForm --> SubmitSignup["API-001 고객 등록 요청"]
    SubmitSignup --> SignupResult{"처리 결과"}
    SignupResult -- "COMMON_001 형식 오류" --> SignupError["입력값 오류 표시, 해당 필드 하이라이트"] --> SignupForm
    SignupResult -- "ACC_001 이미 등록된 실명번호" --> DuplicateNotice["기존 계정 안내 화면, 로그인 유도"] --> Login
    SignupResult -- "ACC_002 실명확인 실패" --> IdentityError["실명확인 실패 안내, 정보 재입력 유도"] --> SignupForm
    SignupResult -- 성공 --> EddCheck{"eddRequired = true?"}

    EddCheck -- 아니오 --> AccountOpenScreen["계좌개설 화면 - 상품유형/초기입금액"]
    EddCheck -- 예 --> EddForm["강화된 고객확인 입력 화면 - 거래목적 상세/자금원천/증빙자료"]
    EddForm --> SubmitEdd["API-013 EDD 등록 요청"]
    SubmitEdd --> EddResult{"처리 결과"}
    EddResult -- "ACC_003 소명 불충분" --> EddError["소명자료 부족 안내, 보완 요청"] --> EddForm
    EddResult -- 성공 --> AccountOpenScreen

    AccountOpenScreen --> SubmitAccountOpen["API-002 계좌개설 요청"]
    SubmitAccountOpen --> AccountOpenResult{"처리 결과"}
    AccountOpenResult -- "ACC_005 CDD 미완료" --> AccountOpenError["CDD 미완료 안내"] --> SignupForm
    AccountOpenResult -- 성공 --> AccountOpenComplete["계좌개설 완료 화면 - 계좌번호 안내"]
    AccountOpenComplete --> Home["홈 화면"]
```

`ACC_005 CDD 미완료` 분기는 정상 흐름에서는 발생하지 않아야 하지만, 회원가입과 계좌개설 사이에 세션이 끊기거나 다른 기기에서 이어서 진행하는 경우를 대비한 방어적 분기다.

## 5. 입출금 플로우

계좌상세 화면에서 입금과 출금으로 각각 진입하는 흐름이다. 둘 다 `Idempotency-Key`를 프론트엔드에서 요청 시점에 자동 생성해 헤더에 실어야 한다는 점이 공통이다.

```mermaid
flowchart TD
    AccountDetail["계좌상세 화면 - 잔액/출금 가능 금액 표시"] --> DepositBtn["입금 버튼"]
    AccountDetail --> WithdrawBtn["출금 버튼"]

    DepositBtn --> DepositForm["입금 금액 입력 화면"]
    DepositForm --> SubmitDeposit["API-006 입금 요청, Idempotency-Key 자동생성"]
    SubmitDeposit --> DepositResult{"처리 결과"}
    DepositResult -- "COMMON_001 형식 오류" --> DepositError["금액 입력 오류 표시"] --> DepositForm
    DepositResult -- 성공 --> DepositComplete["입금 완료 화면 - 변경된 잔액 표시"] --> AccountDetail

    WithdrawBtn --> WithdrawForm["출금 금액 입력 화면 - 출금 가능 금액 안내"]
    WithdrawForm --> SubmitWithdraw["API-007 출금 요청, Idempotency-Key 자동생성"]
    SubmitWithdraw --> WithdrawResult{"처리 결과"}
    WithdrawResult -- "TXN_001 출금 가능 금액 초과" --> InsufficientError["출금 가능 금액 안내, 지급정지 금액이 있으면 함께 표시"] --> WithdrawForm
    WithdrawResult -- "ACC_009 계좌 상태 이상" --> StatusError["정지/해지 계좌 안내"] --> AccountDetail
    WithdrawResult -- 성공 --> WithdrawComplete["출금 완료 화면 - 변경된 잔액 표시"] --> AccountDetail
```

`Idempotency-Key`는 화면 진입 시점이 아니라 실제 제출 버튼을 누르는 시점에 생성해야 한다. 화면 진입 시점에 미리 만들어두면, 사용자가 금액을 여러 번 고쳐 입력한 뒤 제출해도 같은 키가 재사용되어 최초 입력값 기준으로 멱등 처리가 꼬일 수 있다.

출금 거절 안내에는 잔액이 아니라 출금 가능 금액을 기준으로 써야 한다. 2차 인증 대기 중인 이체가 있으면 잔액은 그대로인데 출금 가능 금액만 줄어들기 때문에, 잔액만 보여주면 사용자가 "잔액이 충분한데 왜 거절되는가"를 이해할 수 없다. 두 값이 다를 때만 지급정지된 금액을 함께 노출한다.

## 6. 계좌이체 플로우

가장 복잡한 흐름이다. 입력값 자체 검증, 출금 가능 금액과 상대계좌 상태에 따른 분기, 그리고 임계금액 초과 시 OTP 화면으로 빠지는 분기까지 세 단계의 분기가 겹쳐 있다.

```mermaid
flowchart TD
    AccountDetail["계좌상세 화면"] --> TransferBtn["이체 버튼"]
    TransferBtn --> TransferInput["이체 입력 화면 - 상대계좌/금액/메모"]
    TransferInput --> InputValidate{"입력값 검증"}
    InputValidate -- "TXN_002 동일 계좌" --> SameAccountError["동일 계좌 이체 불가 안내"] --> TransferInput
    InputValidate -- "TXN_003 상대계좌 없음" --> NotFoundError["계좌번호 확인 안내"] --> TransferInput
    InputValidate -- 통과 --> TransferConfirm["이체 확인 화면 - 최종 금액/수수료 확인"]

    TransferConfirm --> SubmitTransfer["API-008 이체 요청, Idempotency-Key 자동생성"]
    SubmitTransfer --> TransferResult{"처리 결과"}
    TransferResult -- "TXN_001 출금 가능 금액 부족" --> InsufficientError["출금 가능 금액 안내"] --> TransferInput
    TransferResult -- "TXN_004 상대계좌 상태 이상" --> CounterpartyError["상대계좌 이용불가 안내"] --> TransferInput
    TransferResult -- "201 COMPLETED, 임계금액 이하" --> TransferComplete["이체 완료 화면"]
    TransferResult -- "202 PENDING_OTP, 임계금액 초과" --> OtpScreen["OTP 입력 화면 - 문자로 발송된 인증번호 입력, 이체 금액이 지급정지로 잡힘"]

    OtpScreen --> SubmitOtp["API-016 OTP 검증 요청"]
    SubmitOtp --> OtpResult{"검증 결과"}
    OtpResult -- "AUTH_004 불일치" --> OtpMismatch["OTP 불일치 안내, 재입력"] --> OtpScreen
    OtpResult -- "AUTH_005 만료" --> OtpExpired["OTP 만료 안내, 지급정지 해제됨, 이체 재시작 유도"] --> TransferInput
    OtpResult -- "TXN_005 이미 처리됨/만료" --> AlreadyProcessed["이미 처리되었거나 취소된 거래 안내"] --> AccountDetail
    OtpResult -- 성공 --> TransferComplete

    TransferComplete --> AccountDetail
```

프론트엔드에서는 API-008 응답의 HTTP 상태코드로 분기해야 한다. 201이면 이체 확인 화면에서 바로 이체 완료 화면으로 넘어가고, 202면 응답에 담긴 `transactionId`를 들고 OTP 화면으로 이동해야 한다.

`TXN_002`, `TXN_003`은 이체 확인 화면까지 가기 전, 입력 화면 단계의 클라이언트 사이드 검증이나 서버 사전 검증으로 걸러지는 것이 이상적이지만, 서버가 최종적으로 다시 검증하므로 이체 확인 화면에서도 이 에러가 돌아올 수 있다. 두 지점 모두에 에러 핸들링을 넣어야 한다.

202 응답을 받은 시점에 이체 금액이 출금계좌의 지급정지 금액으로 잡힌다는 점을 화면에도 반영해야 한다. OTP 화면에 머무는 동안 사용자가 다른 탭에서 계좌를 조회하면 출금 가능 금액이 줄어 있는 것이 정상이므로, OTP 화면에 대기 중인 금액을 명시해두면 혼란을 줄일 수 있다. 인증이 만료되거나 취소되면 지급정지가 해제되므로, OTP 만료 안내 후 계좌 화면으로 돌아갈 때 계좌 관련 조회를 무효화해 최신 값을 다시 받아야 한다.

## 7. 상품가입 플로우

```mermaid
flowchart TD
    Home["홈 화면"] --> ProductTab["상품 탭"]
    ProductTab --> ProductList["API-018 상품목록 조회, 목록 화면"]
    ProductList --> ProductDetail["상품상세 화면 - 이율/최소가입금액/계약기간"]
    ProductDetail --> SubscribeBtn["가입하기 버튼"]
    SubscribeBtn --> SubscribeForm["가입신청 화면 - 출금계좌 선택/가입금액 입력"]
    SubscribeForm --> SubmitSubscribe["API-012 상품가입 요청"]
    SubmitSubscribe --> SubscribeResult{"처리 결과"}
    SubscribeResult -- "PRD_001 최소금액 미달" --> MinAmountError["최소가입금액 안내"] --> SubscribeForm
    SubscribeResult -- "PRD_002 판매중지 상품" --> UnavailableError["판매중지 안내"] --> ProductList
    SubscribeResult -- 성공 --> SubscribeComplete["가입완료 화면 - 계약번호/만기일 안내"]
    SubscribeComplete --> MyContracts["API-019 내 가입상품 조회 화면"]
    MyContracts --> Home
```

## 8. 계좌해지 플로우

```mermaid
flowchart TD
    AccountDetail["계좌상세 화면"] --> CloseBtn["계좌해지 버튼"]
    CloseBtn --> CloseConfirm["해지 확인 다이얼로그"]
    CloseConfirm --> SubmitClose["API-005 계좌해지 요청"]
    SubmitClose --> CloseResult{"처리 결과"}
    CloseResult -- "ACC_008 잔액이 0원 아님" --> BalanceError["잔액을 0원으로 만든 후 재시도 안내"] --> AccountDetail
    CloseResult -- "ACC_010 지급정지 금액 잔존" --> HoldError["인증 대기 중인 이체가 있다는 안내, 완료 또는 취소 후 재시도 유도"] --> AccountDetail
    CloseResult -- 성공 --> CloseComplete["해지완료 화면"]
    CloseComplete --> Home["홈 화면 - 계좌목록에서 제외"]
```

잔액이 0원이어도 지급정지 금액이 남아 있으면 2차 인증 대기 중인 이체가 존재한다는 뜻이므로 해지가 거절된다. 두 에러를 같은 문구로 처리하면 사용자가 무엇을 해야 하는지 알 수 없으니 분리해서 안내한다.

## 9. 프론트엔드 구현 시 참고사항

공통 에러 처리는 화면마다 개별로 짜기보다, API설계.md 2.7절의 공통 에러코드(`COMMON_001`~`COMMON_007`)는 전역 인터셉터에서 한 번에 처리하고, 도메인 에러코드(`ACC_`, `TXN_`, `AUTH_`, `PRD_` 접두어)만 각 화면에서 개별 처리하는 구조를 권장한다. 이렇게 나누면 이 문서의 각 플로우에 그려진 도메인 에러 분기만 화면별로 구현하면 되고, 인증 만료나 서버 오류, 위조 방지 토큰 불일치 같은 공통 케이스는 신경 쓸 필요가 없어진다.

화면 전환은 이 문서에 그려진 순서를 그대로 라우터의 페이지 스택으로 옮기면 된다. 예를 들어 이체 플로우는 이체입력 → 이체확인 → (OTP입력) → 이체완료 순으로 뒤로가기 스택이 쌓이되, 이체완료 화면에서 뒤로가기를 누르면 이체입력이 아니라 계좌상세로 바로 돌아가도록 스택을 정리하는 처리가 필요하다. 이는 이체 완료 후 실수로 중복 이체 화면으로 돌아가는 것을 막기 위함이다.

금액을 바꾸는 요청 이후에는 계좌 관련 조회를 무효화해 최신 잔액과 출금 가능 금액을 다시 받아야 한다. 무효화 시점은 입출금과 이체의 완료 응답, 그리고 2차 인증의 대기 진입과 완료·취소 시점 전부다. 대기 진입 시점을 빠뜨리면 지급정지가 반영되지 않은 출금 가능 금액이 화면에 남는다.
