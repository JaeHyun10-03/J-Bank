# J-Bank 앱 디자인 노트

최종 검증: 2026-07-26 (Figma 파일 실측 기준)

## 문서 성격

J-Bank 모바일 앱 디자인의 작업 노트다. 디자인 토큰, 플로우별 팔레트, 타이포·간격 수치, 화면 인벤토리, 프로토타입 배선, 그리고 Figma 플러그인 작업 시 반복해서 걸렸던 함정을 기록한다.

이 문서의 수치는 전부 Figma 파일을 직접 조회해 확인한 값이다. 이전 판은 기억에 의존해 작성된 부분이 있어 노드 식별자와 좌표가 실제와 어긋나 있었다. 이번 판은 조회 결과로 전면 교체했고, 앞으로도 편집 전에는 반드시 파일을 다시 조회하는 것을 원칙으로 한다.

## 파일 정보

- Figma 파일: Bank (fileKey `rB8P0k4VRswqKrCjlE4c9s`)
- `[01] - 개발 인프라` (0:1) — 비어 있음
- `00. Design Tokens` (49:5) — 디자인 시스템, 1200×1564
- `01. App Screens` (50:5) — 앱 화면 41종, 4893×24065

프로토타입 플로우 시작점 5개: `앱 시작` / `상품 탭` / `J키즈 적금 상세` / `J팜 농장 상세` / `J키즈 통장 상세`

## 디자인 토큰

- **Color/Primitive**: blue 10단계(50~900, 900=`#0114A7` 브랜드 primary), gray 11단계, red/green/yellow 상태색
- **Color/Semantic**: brand, bg, surface, border, text, action, feedback (모두 Primitive alias)
- **Spacing**: 2~64px 13단계 / **Radius**: none~full 7단계
- **Typography**: Display/Title/Heading/Body/Caption/Button 11 스타일
- **아이콘 컴포넌트**: home/bag/gift/star/menu/zap/chevronRight/pencil/refresh/bell/copy/wallet/grid
- **탭바 아이콘 id**: 홈 53:3 / 상품 53:5 / 혜택 53:7 / 서비스 53:9 / 전체 53:11 / chevronRight 53:15
- **3D 일러스트 컴포넌트**: icon3d/coin(57:3), icon3d/trophy(57:10), icon3d/card(57:17)

## 폰트

**Gothic A1**을 사용한다. 실제 파일에서 확인된 웨이트는 Regular / Medium / SemiBold / Bold / ExtraBold 5종이다.

주의할 점은 스타일 문자열 표기다. Gothic A1은 `SemiBold`와 `ExtraBold`처럼 붙여 쓴다. `Semi Bold`로 띄어 쓰면 폰트 로드가 실패한다. 다른 패밀리는 띄어 쓰는 경우가 있으므로, 확신이 없으면 `listAvailableFontsAsync()`로 먼저 확인한다.

## 플로우별 팔레트

같은 앱 안에서도 플로우에 따라 팔레트가 다르다. 서로 섞어 쓰지 않도록 구분해 기록한다.

### 인증·온보딩 플로우

앱 내부 UI와 primary 색이 다르다.

| 토큰 | 값 | 용도 |
|---|---|---|
| NAVY | `#0414A7` | 인증 플로우 CTA (앱 내부 `#4262FF`와 다름) |
| LOGO | `#17008C` | J-Bank 워드마크 |
| ICOBLUE | `#3350F5` | 권한 아이콘 배경 사각형 |
| CHECK | `#2539E9` | 완료 체크마크 |
| INK / SUB / MUT / PH | `#191F28` / `#6B7684` / `#8B95A1` / `#B0B8C1` | 본문·보조·라벨·플레이스홀더 |
| LINE / FIELDBG / TILE | `#E5E8EF` / `#F7F8FB` / `#F7F8FB` | 보더·활성필드·타일 |
| RED / SLATE | `#F04452` / `#4E5968` | 인증 타이머 · 도움말 아이콘 |
| DISABLED CTA | bg `#F2F4F6`, text `#B0B8C1` | 비활성 버튼 |

**입력 필드 규칙**: 값이 비어 있는 필드는 흰색 + `#E5E8EF` 1px 보더, 값이 채워진 필드는 `#F7F8FB` 단색 + 보더 없음.

### 가져오기·휴대폰이체 플로우

| 토큰 | 값 | 용도 |
|---|---|---|
| NAVY | `#0114A7` | 활성 CTA, J 로고 |
| LINE | `#E4E8F0` | 카드·필드 보더, 비활성 CTA 배경 |
| PROMO / TRACK | `#EFF2F6` | 자동충전 프로모 카드, 세그먼트 컨트롤 트랙 |
| CHIP | bg `#EDF3FF` / text `#3B5BFF` | 금액 퀵칩(+1만·+10만·+100만·전액), 캐럿 |
| SLATE | `#333D4B` | 아이콘, "원" 단위 |
| CLEAR | `#C3C9D4` | 입력 지우기 원 |
| DIM | black @ opacity 0.66 | 바텀시트 뒤 딤 오버레이 |
| avatar pastel | `#FFF6D6`/`#C08A1E`, `#F6EDFD`/`#8B5CD6`, `#E4F3FE`/`#2E86DE`, `#FFEEED`/`#E5555A`, `#E8F7EE`/`#2FA36B` | 연락처 아바타 5색 순환 |

**레이아웃**: 콘텐츠 좌우 패딩 20px(다른 화면 24px과 다름), 상단바 56, 상단바에서 타이틀까지 gap 23, 카드·필드·CTA radius 14.

**CTA 규칙**: 전체화면 CTA는 높이 56 + `paddingBottom=28`, 바텀시트 내부 CTA는 높이 54 + 시트 `paddingBottom=40`.

### 상품 상세·약관

| 토큰 | 값 | 용도 |
|---|---|---|
| BLUE accent | `#4262FF` | 히어로 타이틀 둘째 줄 강조 |
| CTA NAVY | `#0114A7` | 플로팅 CTA(적금 만들기 / 농장 만들기 / 통장 만들기) |
| INK / BODY / SUB | `#191F28` / `#4E5968` / `#8B95A1` | 제목 · 본문 · 캡션 |
| ALT bg | `#F7F8FB` | 아코디언 펼침 영역, 섹션 교차 배경 |
| DIVIDER | `#EFF2F7` / `#E9EDF3` | 구분선(연/진) |
| TABLE | header bg `#EFF2F6`, outer border `#333D4B` 1.5px | 금리표 |
| FARM promo | card `#EFF9E8`, chip `#D6FBC4`, text `#4AA239`, 숫자 `#3F8E2E` | J팜 가입2 프로모 |
| FARM slider | track `#EAE6F0`, thumb ring `#5CB646` | J팜 가입3 금액 슬라이더 |
| SEGMENT | 선택 border `#3B5BFF` / bg `#F7F9FF` / text `#1B33C7`, 비선택 border `#E4E8F0` | 기간·금액 세그먼트 |
| CTA disabled | bg `#EFF2F6` / label `#CFD4E1` | 비활성 CTA |
| GRADIENT | `#E4F8DA`(0) → `#FFFFFF`(0.62~1) | J팜 가입3 배경 |

### 타이포·간격 규격

```
좌우 패딩 20 / 상단바 56 / CTA h56 r14, 좌우 20, 하단 28
히어로: gap52 → eyebrow 15 Medium lh22 → gap14 → title 26 ExtraBold lh36 ×2
        → gap34 → 일러스트 → gap44 → 통계행 → gap44
피처섹션: gap52~60 → title 25 ExtraBold lh35 ×2 → gap16 → sub 15 Regular lh24 → 일러스트
아코디언 접힘 행 h=56(상세) / h=62(약관), 라벨 16 Bold
약관 본문 15 Regular lh25 / 본문 소제목 17 ExtraBold lh26 / 주석 14 / 표 셀 14 lh22
링크 행(약관 및 상품설명서) h=52, 15 Regular #4E5968 + chevron-right 18 #B0B8C1
```

## 아이콘 시스템

Lucide 비율을 기준으로 정비했다.

- **제약**: Figma `vectorPaths`는 SVG arc(`a`/`A`)와 shorthand(`H`/`V`)를 지원하지 않아 Lucide SVG를 그대로 붙일 수 없다. M/L/C(큐빅)와 ELLIPSE 조합으로 형태를 재구성했다.
- **chevron 정규화**: 전 화면 50개 chevron(back 11 · 목록 29 · 아코디언 10)을 폭:높이 약 1:2, strokeWeight 2, round cap/join으로 일괄 교체.
- **곡선 우회 기법**: arc 미지원이라 `clipsContent=true` 프레임 안에 동심 ELLIPSE 스트로크를 넣고 흰색 사각형으로 하단을 덮어 호를 표현했다. 바이오인증 지문 일러스트에 사용.
- **더 정밀하게 가려면**: `figma.createNodeFromSvg()`는 arc를 포함한 전체 SVG를 파싱할 수 있다. Lucide SVG 파일을 그대로 임포트하면 픽셀 단위로 맞출 수 있다.
- **아이콘 컬러 규칙**: 브랜드·솔리드 색 타일에는 흰색 글리프, 회색(`#EEF0F6`) 타일에는 항목별 컬러 글리프. strokeWeight 2, round cap/join, 24-box 정중앙.

## 화면 인벤토리 (41종)

x는 500px 간격, 좌표는 실측값이다.

### y=0 — 인증·온보딩 (10)

| 이름 | id | x | 내용 |
|---|---|---|---|
| Auth1-Splash | `172:1732` | 0 | J-Bank 워드마크 + 제이뱅크 시작하기 + "이미 계좌가 있으신가요?" |
| Auth2-Permission | `172:1741` | 500 | 권한 안내 8종(연락처/카메라/사진앨범/Face ID/추적/위치/마이크/음성인식) |
| Auth3-Onboard | `172:1844` | 1000 | 3스텝 인디케이터(본인인증 → 회원가입 → 통장과 카드 만들기) |
| Auth4-Name | `172:1866` | 1500 | 이름 입력 (CTA 없음, 키보드가 덮는 구조) |
| Auth5-Ssn | `172:1874` | 2000 | 주민등록번호 앞/뒷자리 + 이름 확인 |
| Auth6-Phone | `172:1892` | 2500 | 통신사 드롭다운 + 휴대폰번호 |
| Auth7-OtpCode | `172:1919` | 3000 | 인증번호 입력 + 재요청 pill + 타이머 + 도움말 |
| Auth8-DeviceDone | `172:1955` | 3500 | 기기인증 완료 체크마크 |
| Auth9-PinSetup | `172:1969` | 4000 | 간편비밀번호 등록, 6칸 대시 인디케이터 |
| Auth11-Login | `172:1985` | 4500 | 로그인(간편비밀번호), 3/6 입력 상태 + 보안 숫자키패드 |

### y=1500 — 홈과 알림 (2)

| 이름 | id | x | 높이 |
|---|---|---|---|
| Home | `54:22` | 0 | 956 |
| Detail - Notifications | `133:1929` | 500 | 878 |

### y=3000 — 가져오기 (5)

| 이름 | id | x | 내용 |
|---|---|---|---|
| Pull1-Amount | `133:2641` | 0 | 가져올 통장 카드 + 금액 필드 + 자동충전 프로모 |
| Pull2-AccountSheet | `133:2838` | 500 | 계좌 선택 바텀시트(h=283 @y569) |
| Pull3-AmountSheet | `133:2897` | 1000 | 금액 입력 바텀시트(h=542 @y310), 퀵칩 4종 + 숫자키패드 |
| Pull4-AmountFilled | `133:2996` | 1500 | 10,000원 입력 상태 + 지우기 + 활성 CTA |
| Pull5-Complete | `133:2685` | 2000 | 완료, 상하단 보더만 있는 요약 블록 3행 |

### y=4500 — 이체와 휴대폰이체 (7)

| 이름 | id | x | 높이 |
|---|---|---|---|
| Transfer 1 - Account | `65:68` | 0 | 821 |
| Transfer 2 - Amount | `66:69` | 500 | 821 |
| Transfer 3 - Confirm | `68:69` | 1000 | 821 |
| Transfer 4 - Complete | `69:70` | 1500 | 821 |
| PhoneTr1-Terms | `133:3098` | 2500 | 852, 약관동의 바텀시트(h=458 @y394) |
| PhoneTr2-Contacts | `133:2711` | 3000 | 852, 세그먼트 + 검색 + 연락처 6행 |
| PhoneTr3-Amount | `133:2770` | 3500 | 852 |

### y=6000 — 상품 탭 (3)

| 이름 | id | x | 높이 |
|---|---|---|---|
| Products | `59:41` | 0 | 842 |
| Products - Loan | `133:2186` | 1000 | 848 |
| Products - Card | `133:2290` | 2000 | 721 |

### y=7000 — 상품 상세 (4)

| 이름 | id | x | 크기 | 내용 |
|---|---|---|---|---|
| ProdJKids-Detail | `158:82` | 0 | 393×2478 | 히어로/재가입 우대/우대금리/함께관리 + 아코디언 4행 + 플로팅 CTA |
| ProdJFarm-Detail | `163:82` | 500 | 393×3134 | 히어로/이자재투자/과일캐릭터/자유입금/체험영상 + 아코디언 4행 |
| Detail - Loan | `133:1796` | 1000 | 393×821 | |
| Detail - CheckCard | `133:2137` | 2020 | 393×821 | |

### y=10000 / 10500 — 약관 (2)

| 이름 | id | x | 크기 | 내용 |
|---|---|---|---|---|
| ProdJKids-Terms | `160:82` | 0 | 393×5839 | 상품상세·금리안내·이용안내·약관 4개 아코디언 전개 |
| ProdJFarm-Terms | `166:82` | 500 | 393×5220 | 회차별 월 지급이자 12행, 기본금리표, 만기 후 금리표 |

### y=16000~18000 — 통장 상세와 가입 플로우 (4)

| 이름 | id | x | y | 내용 |
|---|---|---|---|---|
| ProdJKidsBank-Detail | `171:82` | 0 | 16000 | J키즈 통장 히어로, 금리/수수료 통계 |
| JFarmJoin1-Account | `169:82` | 500 | 16000 | 연결통장 선택 카드 |
| JFarmJoin2-Options | `169:102` | 500 | 17000 | 수확금 프로모 + 기간/금액 세그먼트 + 과일 선택 |
| JFarmJoin3-Fruit | `170:82` | 500 | 18000 | 과일 캐릭터 + 금액 슬라이더 + 과일 칩 8종, 그라디언트 배경 |

### y=20000 이후 — 나머지 탭 (4)

| 이름 | id | x | y | 높이 |
|---|---|---|---|---|
| Benefits | `61:46` | 0 | 20000 | 1040 |
| Services | `63:56` | 0 | 21500 | 961 |
| AllMenu - Transfer | `133:2476` | 0 | 23000 | 1065 |
| Detail - Settings | `133:1870` | 500 | 23000 | 714 |

### 롱스크롤 프레임 규칙

상품 상세와 약관 4개는 852 고정이 아니라 콘텐츠 전체 높이를 hug하는 세로 오토레이아웃 프레임이다. 상단바를 첫 자식으로 넣고, 플로팅 CTA만 `layoutPositioning='ABSOLUTE'` + `y = F.height - 56 - 28`로 붙인다. 좌표는 프레임 높이가 확정된 마지막에 설정해야 한다. CTA가 콘텐츠를 가리지 않도록 끝에 104px 스페이서를 먼저 append한다.

## 주요 노드 식별자

```
홈 진입 버튼
  가져오기 = 55:17(frame) / 55:18(text)
  이체하기 = 55:19(frame) / 55:20(text)
  자동이체 = 56:13 / 56:17
  자동충전 = 56:18 / 56:22
  이체     = 56:43

상품탭 카드
  J키즈 적금 카드 = 60:44 (nameRow 60:47 / text 60:48)
  J팜 농장 카드   = 60:59 (nameRow 60:63 / text 60:64)
```

## 프로토타입

실측 결과 노드 이동 링크 70개, 뒤로가기 링크 11개로 총 81개이며 **끊어진 대상은 0건**이다. 플로우 시작점은 5개다.

```
앱 시작           → Home(54:22)
상품 탭           → Products(59:41)
J키즈 적금 상세    → ProdJKids-Detail(158:82)
J팜 농장 상세      → ProdJFarm-Detail(163:82)
J키즈 통장 상세    → ProdJKidsBank-Detail(171:82)
```

주요 배선은 다음과 같다.

- 탭바(홈/상품/혜택/서비스/전체) → 각 메인 탭
- 필터칩 → 변형 화면
- 상품탭 카드 → 상품 상세 → 약관 → 가입 3단계
- 이체 4단계, 가져오기 5단계, 휴대폰이체 3단계
- back chevron → BACK

전환 효과는 인증 플로우가 PUSH LEFT 0.3s EASE_OUT, 상세·약관·가입 플로우가 SMART_ANIMATE 0.3s EASE_OUT, 완료 화면 복귀가 DISSOLVE다.

## 현재 범위에서 빠진 화면 (11)

이전 판은 화면이 52종이라고 기록했으나 현재는 41종이다. 차이나는 11개는 결함이 아니라 **의도적으로 삭제한 것**이다.

| 화면 | 상태 |
|---|---|
| Auth10-Bio | 바이오인증 기능 자체를 범위에서 제외. 다시 만들지 않음 |
| Detail - JKids (요약 구버전) | 롱스크롤 상세로 대체됨. 다시 만들지 않음 |
| Detail - JFarm (요약 구버전) | 롱스크롤 상세로 대체됨. 다시 만들지 않음 |
| AllMenu | 나중에 추가 예정 |
| Products - Invest | 나중에 추가 예정 |
| Benefits - Signup | 나중에 추가 예정 |
| Benefits - GovSubsidy | 나중에 추가 예정 |
| Benefits - Payment | 나중에 추가 예정 |
| Services - Invest | 나중에 추가 예정 |
| Detail - MyAssets | 나중에 추가 예정 |
| Detail - Stocks | 나중에 추가 예정 |

앞의 3개는 범위에서 완전히 빠졌고, 뒤의 8개는 추후 추가 대상이다. 8개를 다시 만들 때 아이콘 매핑은 `09_J-Bank_아이콘정렬-수정노트.md`에 남아 있다.

### 삭제에 따른 배선 보정

바이오인증이 Auth9와 Home 사이를 잇고 있었기 때문에, 삭제 후 온보딩이 Auth9에서 끝나고 로그인도 진행되지 않는 상태가 되어 있었다. 2026-07-27에 다음을 연결해 해소했다.

| 출발 | 도착 | 전환 |
|---|---|---|
| Auth9-PinSetup 프레임 | Home | PUSH LEFT 0.3 EASE_OUT |
| Auth11-Login 키패드(`172:2009`) | Home | DISSOLVE 0.3 EASE_OUT |
| Products 칩 `대출`(`59:56`) | Products - Loan | SMART_ANIMATE 0.3 |
| Products 칩 `카드`(`59:59`) | Products - Card | SMART_ANIMATE 0.3 |

뒤의 두 건은 목적지 화면이 존재하는데 링크만 없던 경우다.

### 목적지 없이 남은 조작 요소 (의도적 보류)

삭제된 화면을 가리키던 자리에 조작 요소만 남은 곳이 있다. **UI는 그대로 두기로 결정했다.** 해당 화면을 나중에 추가할 예정이고, 그때 자리와 배선만 이으면 되기 때문이다. 지금 제거하면 화면을 다시 만들 때 탭바와 칩을 원복해야 한다.

| 요소 | 위치 | 연결 대상(추후) |
|---|---|---|
| 탭바 `전체` 항목 | Home, Products, Benefits, Services 4개 루트 화면 | AllMenu |
| Services 칩 `투자서비스`, `투자투데이` | Services | Services - Invest |
| Benefits 필터칩 | Benefits | Benefits - Signup / GovSubsidy / Payment |

따라서 프로토타입을 시연할 때 이 요소들은 반응하지 않는 것이 정상이다. 결함으로 오인하지 않도록 여기에 기록해둔다.

금액 퀵칩(`+1만`, `+10만`, `+100만`, `전액`)과 각 화면의 현재 탭 항목은 원래 링크가 없어도 정상이므로 이 목록에서 제외했다.

## 레이아웃 규칙

- 배경 `#EDF1F7` + 흰색 라운드 카드(radius 20), 좌우 마진 16px. 그림자는 최소로 쓰고 플랫하게 유지한다.
- 콘텐츠 좌우 패딩 24px, 상단바(뒤로가기/취소) 좌우 패딩도 24px, 상단바 높이 50px.
  - 단, 가져오기·휴대폰이체·상품상세 플로우는 패딩 20px, 상단바 56px.
- 탭바: 상단 구분선 + 5탭 균등(FILL sizing, 컨테이너 393 고정), 활성 탭만 브랜드 블루. 상태바는 두지 않는다.
- 인증 화면 CTA: 좌우 24px, 높이 56px(패딩 16), radius 14, 하단 여백 28px → CTA 상단 y=768.
- 프레임 기준 폭 393px, 가로 500px 간격.

## 작업 시 함정

Figma 플러그인 작업에서 반복해서 걸렸던 것들이다.

- `figma.createAutoLayout()`은 기본 흰색 fill이 들어간다. 구조용 컨테이너는 반드시 `.fills=[]`를 명시한다.
- `layoutSizingHorizontal='FILL'`은 반드시 `appendChild` 이후에 설정한다.
- `layoutSizing*`(FIXED/HUG/FILL)과 `*AxisSizingMode`(FIXED/AUTO)는 다른 열거형이다. 섞어 쓰면 값 거부 오류가 난다.
- `figma.setCurrentPageAsync()`는 한 스크립트에서 한 번만 호출한다. 페이지 컨텍스트는 호출마다 초기화된다. 여러 페이지를 다뤄야 하면 페이지당 스크립트를 따로 만들어 병렬로 실행한다.
- Figma 노드는 임의 프로퍼티를 거부한다. `node.__foo = x`는 `TypeError`가 된다. 헬퍼 함수에서 상태를 노드에 임시 저장하지 말고 생성과 마운트를 한 함수 안에서 끝낸다.
- `node.query()` 셀렉터는 `/`를 파싱하지 못한다. `cta/확인`처럼 슬래시가 들어간 이름은 `findOne(n => n.name === '…')`으로 찾는다.
- 텍스트를 수정할 때는 `getStyledTextSegments(['fontName'])`로 현재 폰트를 읽어 `loadFontAsync`를 먼저 호출한다. 기본 폰트를 하드코딩하면 로드 실패가 난다.
- 한쪽 면만 보더를 그릴 때는 `strokeTopWeight`/`strokeBottomWeight`/`strokeLeftWeight`/`strokeRightWeight`를 쓴다.
- 오토레이아웃 프레임 안에 절대배치 자식을 넣으려면 `child.layoutPositioning='ABSOLUTE'` 설정 후 `x`/`y`/`resize()`를 하되, 부모 높이가 확정된 뒤에 좌표를 넣는다.
- 줄바꿈되는 텍스트는 `textAutoResize='HEIGHT'`와 명시적 폭을 함께 설정한다. `FILL`만 주면 폭이 0에 가깝게 붕괴한다.
- 텍스트 일부만 색을 바꾸려면 `text.setRangeFills(start, end, [paint])`를 쓴다.
- 위에서 아래로 흐르는 선형 그라디언트는 `gradientTransform:[[0,1,0],[-1,0,1]]`이다.
- 아이콘 프레임 안에서 만든 ELLIPSE/RECTANGLE은 `.strokes=[]`를 명시하지 않으면 기본 스트로크가 남는다.
- **프로토타입 플로우 시작점 이름은 텍스트나 레이어명 일괄 치환에 걸리지 않는다.** `page.flowStartingPoints`를 배열째로 재할당해야 한다. 브랜드 개명 때 이것만 남아 있었다.
- 프레임 좌표에 `7.1e-15` 같은 부동소수점 잔여가 생길 수 있다. 좌표로 화면을 선별하는 스크립트를 짤 때 정수 매칭이 실패하는 원인이 된다.

## 남은 품질 이슈

1. 3D 일러스트는 자체 제작본이다. 정식 에셋으로 교체 여지가 있다.
2. 이모지 글리프가 렌더되지 않아 색상 원으로 대체한 지점이 있다.
3. 정밀한 Lucide 아이콘은 `createNodeFromSvg()`로 임포트하면 더 정확해진다.
4. Pull2의 타행 로고는 텍스트 근사치이며 실제 워드마크가 아니다.
6. Pull1 자동충전 프로모 아이콘은 회전 라운드 사각형 + 코인 타원 조합의 자체 근사치다.
7. PhoneTr2 돋보기 아이콘이 축소 배율에서 다소 얇게 보인다. 실배율 확인이 필요하다.
8. 상품 상세 일러스트(과일 캐릭터, 아이 얼굴, 돈다발, 하트 원화)는 벡터 근사치다. 3D 렌더 에셋을 확보하면 `upload_assets`로 교체할 수 있다.
9. ProdJKidsBank-Detail(171:82)은 히어로 섹션만 있고 하단 콘텐츠가 비어 있다.

## 다음 후보

- 추후 추가 대상 8종 제작과 배선(탭바 전체 항목, Services·Benefits 필터칩이 이미 자리를 잡고 있으므로 링크만 이으면 된다)
- J키즈 통장 상세 나머지 섹션과 약관 페이지
- 계좌관리/카드관리/대출관리 설정 리스트
- 탭바 아이콘을 실제 Lucide SVG로 임포트
