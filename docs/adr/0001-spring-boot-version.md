# ADR 0001: Spring Boot 버전을 3.3에서 3.5.16으로

## 상태

승인됨. 2026-07-28 환경 세팅 시점.

## 배경

구현계획 문서(`07_J-Bank_구현계획.md`)는 Spring Boot 3.3을 확정 버전으로 명시한다. 환경 세팅 시점에 Spring Initializr(`start.spring.io`)를 통해 실제 프로젝트를 생성하려 하자, 서비스가 `Spring Boot compatibility range is >=4.0.0`을 반환하며 3.3을 포함한 3.x 전체를 거부했다. Initializr는 Spring Boot의 OSS 지원 기간이 끝난 버전을 목록에서 제외하는데, 3.3은 그 대상이 되었다.

## 결정

Maven Central을 직접 조회해 3.x 라인의 최신 패치인 3.5.16을 확인했다. Gradle 프로젝트를 Initializr 대신 수작업으로 스캐폴딩하고 이 버전을 지정했다.

4.x로 올리는 대신 3.5.16을 택한 이유는 두 문서의 전제를 그대로 지키기 위해서다. API설계 문서와 프론트엔드기술스택 문서는 Spring Security 6 필터체인 API를 전제로 서술되어 있고, 3.5는 Spring Framework 6·Spring Security 6 라인을 유지한다. 4.x는 Spring Framework 7·Spring Security 7로 메이저 업그레이드되어 필터체인 구성 방식과 여러 API가 달라지므로, 이 시점에 올리면 아직 쓰지도 않은 설계 문서 서술과 실제 코드가 어긋나는 위험을 만든다.

## 근거

되돌리는 비용 기준(구현계획 7.1절)을 그대로 적용한다. 패치 버전 차이(3.3 → 3.5)는 API 호환이 유지되므로 지금 반영해도 나중에 되돌릴 것이 없다. 반면 메이저 버전 차이(3.x → 4.x)는 인증 설정과 여러 스타터 API가 갈라져, 설계 문서의 인증·보안 서술을 전부 재검토해야 한다. 그 재검토는 W3 인증 구현 시점에 필요가 확인된 뒤에 판단하는 것이 맞다.

## 영향

- `apps/jbank-api/build.gradle.kts`의 Spring Boot 플러그인 버전이 3.5.16
- 그 외 설계 문서의 Spring Security 6, Spring Batch 등 서술은 그대로 유효
