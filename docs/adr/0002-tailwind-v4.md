# ADR 0002: Tailwind CSS v4 적용

## 상태

승인됨. 2026-07-28 환경 세팅 시점.

## 배경

프론트엔드기술스택 문서(`05_J-Bank_프론트엔드기술스택.md`)는 "Tailwind CSS"만 명시하고 메이저 버전을 못박지 않았다. `create-next-app@14`로 스캐폴딩하면 기본으로 Tailwind v3.4.1이 깔린다. 세팅 도중 사용자가 v3가 구버전이라는 점을 지적해 v4로 교체를 요청했다.

## 결정

Tailwind v3 관련 패키지(`tailwindcss`)와 `tailwind.config.ts`를 제거하고 v4(`tailwindcss`, `@tailwindcss/postcss`)를 설치했다. `shadcn/ui` CLI를 v4가 설치된 상태에서 재실행해 v4 방식(설정 파일 대신 `app/globals.css`의 `@theme`/CSS 변수 기반)으로 다시 초기화했다. `postcss.config.mjs`의 플러그인 키를 `tailwindcss`에서 `@tailwindcss/postcss`로 바꿨다.

이 과정에서 shadcn이 기본으로 넣은 `next/font/google`의 `Geist` 폰트가 이 프로젝트의 Next.js 14.2.35 번들에 없어 빌드가 깨졌다. 작업노트에 기록된 브랜드 폰트가 Gothic A1이므로, 임시방편으로 폰트를 고치는 대신 애초에 맞는 폰트로 교체했다.

## 근거

문서가 버전을 못박지 않은 항목이라 편차가 아니라 문서 여백을 채우는 판단이다. 되돌리는 비용도 낮다. 지금은 화면 코드가 없어 유틸리티 클래스 문법 차이(v3의 `tailwind.config.ts` 확장 vs v4의 `@theme` 인라인)로 인한 재작업 대상이 없다.

## 영향

- `apps/frontend/package.json`의 `tailwindcss`가 v4, `@tailwindcss/postcss` 신규 추가
- `apps/frontend/tailwind.config.ts` 삭제, 설정은 `app/globals.css`의 `@theme` 블록으로 이동
- `apps/frontend/app/layout.tsx`의 폰트를 `next/font/google`의 `Gothic_A1`로 교체(작업노트 "폰트는 Gothic A1" 근거)
