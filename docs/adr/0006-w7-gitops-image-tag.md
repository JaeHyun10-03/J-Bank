# ADR 0006: ArgoCD 도입 후 이미지 태그 전달 방식을 Git 커밋으로 변경

## 상태

승인됨. 2026-08-31 W7 ArgoCD 도입 직후, 실제 클러스터에 배포해보다 발견.

## 배경

`infra/helm/jbank-api/values.yaml`/`values-prod.yaml`에는 원래 "image.repository/tag는
이 파일에 두지 않는다 — backend-cd.yml이 배포 시점에 `--set`으로 넘긴다"는
주석이 있었다. 이건 CI가 `helm upgrade --install ... --set
image.tag=$GITHUB_SHA`처럼 클러스터에 직접 배포하는 걸 전제로 한
설계였는데, 실제로 `backend-cd.yml`엔 그 `helm upgrade` 스텝 자체가
아직 구현돼 있지 않았다(ECR 푸시까지만 있었음) — 즉 지금까지는 실행된
적 없는 계획이었다.

오늘 ArgoCD를 실제 클러스터에 붙이고 `jbank-api-dev` Application을
`syncPolicy.automated.selfHeal: true`로 켠 채로 이 계획대로 동작을
확인해보니 근본적인 충돌이 드러났다. ArgoCD는 Git을 진실의 원천으로
본다 — `helm --set`으로 클러스터 상태를 Git 선언과 다르게 만들면,
selfHeal이 그 차이를 "드리프트"로 보고 다음 동기화 주기에 Git에 선언된
값(구버전 이미지 태그)으로 그대로 되돌려버린다. CI가 새 이미지를 배포한
직후 ArgoCD가 곧바로 원복시키는 셈이라, GitOps를 켜는 순간 기존 CD
계획 자체가 무효가 된다.

## 결정

이미지 레지스트리·태그를 `infra/helm/jbank-api/values-image.yaml`이라는
새 파일 하나에 몰아넣고, 이 파일만 Git에 커밋된 desired state로 관리한다.
`backend-cd.yml`은 ECR 푸시가 끝나면 `helm upgrade --set`을 실행하는
대신 이 파일을 새 태그로 덮어쓰고 `git commit && git push`한다. ArgoCD
Application(`kubectl_manifest.app_dev`/`app_prod`, `modules/gitops`)의
`helm.valueFiles`에 이 파일을 추가해서, 커밋이 들어오면 dev는 자동
동기화로, prod는 수동 승인 후 그 태그를 그대로 끌어가게 했다.

`values.yaml`/`values-dev.yaml`/`values-prod.yaml`은 계정 ID가 든
레지스트리 URL을 넣지 않으려던 원래 취지를 그대로 유지한다 — 실제
레지스트리·태그는 이 새 파일 하나로만 흐르고, CI 워크플로 권한을
`contents: read`에서 `contents: write`로 올렸다.

## 근거

- GitOps의 핵심 전제는 "클러스터에 무엇이 떠 있어야 하는지는 Git만 보고
  안다"는 것이다. 클러스터에 직접 값을 꽂는 경로(`--set`, `kubectl
  edit`, 수동 `helm upgrade`)가 하나라도 남아있으면 selfHeal이 있는
  Application에서는 그 경로 자체가 무의미해진다 — 이번처럼 조용히
  되돌려지거나, selfHeal을 끄면 반대로 진짜 드리프트를 못 잡는다.
- 대안으로 "prod처럼 selfHeal을 끈다"도 고려했지만, dev는 자동 배포가
  이번 주 완료 기준(무중단 배포 검증)에 필요해서 selfHeal을 유지해야
  했다. 문제를 이미지 태그 쪽에서 없애는 게 더 근본적인 해결이다.
- 별도 파일로 분리한 이유는 `values-dev.yaml`/`values-prod.yaml`에
  레지스트리 URL(계정 ID 포함)을 박아 넣지 않으려던 기존 결정과
  충돌하지 않게 하기 위해서다 — CI가 매번 덮어쓰는 파일과, 사람이 손으로
  관리하는 리소스·환경 설정 파일을 분리해두면 실수로 CI가 사람이 쓴
  값을 지우는 사고도 막는다.

## 영향

- `backend-cd.yml`: 권한이 `contents: read` → `contents: write`로
  바뀌었고, 이미지 푸시 후 커밋·푸시 스텝이 추가됐다.
- `infra/helm/jbank-api/values-image.yaml` 신규 — CI가 자동으로 덮어쓰는
  파일이라 사람이 직접 고치면 다음 배포에서 사라진다.
- `modules/gitops`의 Application 템플릿에 이 파일이 `helm.valueFiles`
  세 번째 항목으로 들어간다.
