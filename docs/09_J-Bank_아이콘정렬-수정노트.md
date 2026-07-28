# J-Bank 아이콘·정렬 전면 점검

작업일: 2026-07-24

## 수정 배경

리뷰 결과 세 가지 문제가 지적됐다. 아이콘 대부분이 가운데 정렬되지 않았고, 텍스트 줄바꿈 정렬이 어긋났고, 혜택 상세의 흰 박스 배경이 어색했다. 전 페이지를 점검했다.

## 핵심 원인

- 리스트 타일 아이콘이 속이 빈 색 블록이나 색 점 자리표시자여서 미완성처럼 보였고, 그 때문에 정렬도 어긋나 보였다.
- 카드의 무한 기호가 절대좌표로 우하단에 배치되어 중앙정렬이 되지 않았다.
- CheckCard 상세의 혜택 리스트가 회색 라운드 박스에 담겨 흰 박스가 떠 있는 것처럼 보였고, 이모지가 렌더되지 않았다.

## 조치

### 1) 무한 기호 중앙정렬

- Products-Card 배너 미니카드: 오토레이아웃 center 정렬로 전환.
- CheckCard 상세 대형카드: 절대좌표를 재계산해 정중앙으로. 브랜드 로고만 좌상단에 남김.

### 2) 흰 박스 제거

CheckCard 혜택 리스트의 회색 박스를 제거하고 흰 배경 + 구분선 방식의 평범한 행으로 바꿨다. 렌더되지 않던 이모지는 Lucide 미니 아이콘(bus/coffee/tv)으로 교체해 회색 타일 중앙에 배치했다.

### 3) 자리표시자 아이콘을 Lucide 글리프로 교체 (약 50개)

Figma `vectorPaths`가 arc와 shorthand를 지원하지 않으므로 M/L/C/Z와 ellipse·rect 프리미티브로 Lucide 형태를 재구성했다. 24-box 프레임을 타일 정중앙에 배치했다.

| 화면 | 글리프 색 | 아이콘 |
|---|---|---|
| Benefits - Signup | 흰색 | smartphone / home / credit-card / wallet / banknote |
| Benefits - GovSubsidy | 흰색 | trending-up / home / sun / gift / home |
| Benefits - Payment 그리드 | 흰색 | coffee / utensils / package / film / sparkles / shopping-cart |
| Benefits 메인 인기혜택 | 컬러 | help-circle / gift / ticket / percent / globe |
| Services 생활 | 컬러 | wallet / sprout / trophy / help-circle / ticket / pie-chart / trending-up |
| Services - Invest | 컬러 | candle / calendar / book / bar-chart / coins / wallet |
| AllMenu - Transfer | 흰색 | send / smartphone / calendar / receipt / banknote / users / wallet / landmark / plus-circle / eye-off |
| Products - Invest | 흰색 | flag / landmark |

## 아이콘 컬러 규칙

- 브랜드·솔리드 색 타일에는 흰색 글리프
- 회색(`#EEF0F6`) 타일에는 항목별 컬러 글리프
- strokeWeight 2, round cap/join, 24-box 정중앙

## 상태

당시 25개 화면 렌더 검수를 마쳤고 아이콘 정렬, 글리프, 박스 이슈를 해소했다. 제휴사 로고가 필요한 자리는 저작권 문제를 피해 카테고리 Lucide 글리프로 대체했다.

## 주의

이 노트에 나열된 화면 가운데 Benefits - Signup, Benefits - GovSubsidy, Benefits - Payment, Services - Invest, Products - Invest, AllMenu는 이후 범위에서 제외되어 현재 파일에 없다. 의도적으로 삭제한 것이므로 복구 대상이 아니다.

따라서 이 문서에서 현재 유효한 부분은 무한 기호 중앙정렬, CheckCard 흰 박스 제거, 그리고 아이콘 컬러 규칙이다. 삭제된 화면의 아이콘 매핑은 기록으로만 남긴다. 범위 제외 화면 목록은 앱디자인노트를 참조한다.
