# j-bank

J-Bank 코어시스템 저장소. 설계 문서는 `docs/`에 있다. 시작은 `docs/README.md`부터.

## 요구 도구

| 도구 | 버전 |
|---|---|
| Java | 21 (Temurin/Zulu 무관, `apps/jbank-api/gradle.properties`가 `/opt/homebrew/opt/openjdk@21`을 가리킨다) |
| Docker / Docker Compose | 최신 |
| Node.js | 20 이상 |

## 최초 세팅

```bash
scripts/bootstrap.sh
```

도구 버전 확인, 프론트엔드 의존성 설치, 로컬 인프라(core 프로파일) 기동까지 한 번에 한다.

## 로컬 실행

```bash
# 인프라: PostgreSQL 16 + Redis 7
scripts/dev.sh core

# 백엔드
cd apps/jbank-api
./gradlew bootRun --args='--spring.profiles.active=local'
# http://localhost:8080/actuator/health
# http://localhost:8080/swagger-ui.html

# 프론트엔드
cd apps/frontend
npm run dev
# http://localhost:3000
```

메시징(Kafka), 관측 스택(Prometheus/Grafana/Loki)이 필요하면 프로파일을 더한다.

```bash
scripts/dev.sh core messaging
scripts/dev.sh core observability
```

전부 동시에 띄우는 단축 명령은 두지 않는다. 성능 측정 조건이 매주 달라지는 것을 막기 위해서다(`infra/compose/docker-compose.yml` 프로파일 구성, `docs/10_J-Bank_폴더구조.md` 7절).

인프라를 내리려면 `scripts/clean.sh`.

## 저장소 구조

```
apps/jbank-api/   Spring Boot 단일 모듈, 도메인 패키지 경계 + ArchUnit
apps/frontend/    Next.js 14 App Router
infra/            Docker Compose, Dockerfile, Terraform, Helm
contracts/        OpenAPI 스냅샷, 수동 호출 컬렉션
perf/             k6 스크립트와 주차별 결과
docs/             설계 문서, ADR, 런북
```

전체 트리와 근거는 `docs/10_J-Bank_폴더구조.md`.

## 문서와의 편차

작업노트(`docs/00_프로젝트-작업노트.md`) 작성 시점 이후 외부 생태계가 바뀐 지점 두 가지를 환경 세팅 시점에 반영했다. 근거는 `docs/adr/0001-spring-boot-version.md`, `docs/adr/0002-tailwind-v4.md`.

| 문서 결정 | 실제 적용 | 이유 |
|---|---|---|
| Spring Boot 3.3 | Spring Boot 3.5.16 | 3.3은 Spring Initializr에서 더 이상 제공되지 않음(OSS 지원 종료). Spring Security 6 등 문서가 전제한 3.x API는 동일하게 유지되는 3.5 라인으로 대체 |
| Tailwind CSS(버전 미지정) | Tailwind CSS v4 | `create-next-app@14`가 기본으로 깔던 v3 대신 최신 v4로 교체(shadcn/ui v4 설정 포함) |

## 아직 없는 것

`db/migration`(Flyway 스키마), 도메인 엔티티·API, `perf/k6`, `scripts/seed.sh`, `infra/terraform` 실제 구성, `infra-plan.yml`·`perf.yml` 워크플로는 구현계획 W1 이후 주차에서 채운다. 이 시점은 도구 설치와 뼈대 기동 검증까지다.
