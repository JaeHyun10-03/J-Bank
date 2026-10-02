# 운영 반영 확인 (AC-03·REQ-08·OPS-07)

- 병합: PR #11(7e89e43, 01:47:31Z), PR #12(2ce16ec, 01:47:56Z). backend-cd(2ce16ec) 01:47:59Z 시작, 성공. 인스턴스가 켜져 있어 배포 실행.
- 방법: SSM Run Command(AWS-RunShellScript, 읽기 전용) 37bdc5aa-2140-4f9a-a89a-828702a5b768, 2026-10-02T02:21:27Z(11:21 KST).
- 판정 기준(결과 리뷰 1회차 권고-1): `logger=provisioning.dashboard`의 error·warn만 대시보드 프로비저닝 오류로 본다. plugins·alerting 폴더가 없어 나는 error 2건은 이번 변경과 무관한 기존 항목.

## SSM 결과

| 항목 | 결과 |
| --- | --- |
| 운영 저장소 HEAD | `2ce16ec Merge pull request #12 from JaeHyun10-03/feat/ops-dashboard-panels` |
| api | `Up 31 minutes (healthy)` (배포 재기동 뒤 정상) |
| grafana | `Up 2 hours` (재시작 없음) |
| Grafana 컨테이너 안 대시보드 파일 제목 | `"title": "J-Bank 운영"` |
| 컨테이너 안 파일의 `"id":` 줄 수 | 17 (행 4 + 패널 13) |
| 컨테이너 안 파일 md5 | `3f37b2322c52971a31a406cca54d49e1` |
| 운영 저장소 파일 md5 | `3f37b2322c52971a31a406cca54d49e1` (컨테이너와 같음, 로컬 HEAD 파일도 같음) |
| `logger=provisioning.dashboard` error·warn (01:40Z 이후) | 없음 |
| 오늘 `provisioning.dashboard` 로그 | 00:01:04Z `starting/finished to provision dashboards`(부팅 시) 뿐. 30초 주기 재로드는 info 로그를 남기지 않음 |
| 그 밖의 provisioning error(참고) | `provisioning.alerting` 폴더 없음 1건, `provisioning.plugins` 폴더 없음 1건(부팅 시, 기존 무관 항목) |

실행 명령(`/opt/jbank/infra/compose`, `C="docker compose -f docker-compose.prod.yml"`):

```bash
git -c safe.directory='*' -C /opt/jbank log -1 --format='%h %s'
$C ps --format '{{.Service}} | {{.Status}}' api grafana
$C exec -T grafana sh -c 'grep -m1 "\"title\"" /etc/grafana/provisioning/dashboards/json/jbank-transfer.json; grep -c "\"id\":" /etc/grafana/provisioning/dashboards/json/jbank-transfer.json; md5sum /etc/grafana/provisioning/dashboards/json/jbank-transfer.json'
md5sum observability/provisioning/dashboards/json/jbank-transfer.json
$C logs grafana --since 2026-10-02T01:40:00Z 2>&1 | grep 'logger=provisioning.dashboard' | grep -E 'level=(error|warn)'
```

## 사용자 화면 확인

- 2026-10-02 11:21 KST 무렵 사용자: "머지했고 대시보드 화면 확인했어". 운영 Grafana(https://grafana.j-bank.site/d/jbank-transfer)에서 새 대시보드를 확인함.
- Grafana 로그인은 사용자 본인이 수행(비밀번호는 Claude가 다루지 않음).

## 판정

REQ-08 충족: 운영 Grafana가 새 대시보드 파일을 읽고 있고(컨테이너 안 제목·md5 일치), 대시보드 프로비저닝 오류 0건, 사용자 화면 확인.
