# ADR 0011: 운영 EC2를 평일 09~18시에만 켠다

## 상태

승인됨. 2026-09-29. ADR 0010(EC2 단일 인스턴스)의 운영 방식을 보완한다.

## 배경

ADR 0010으로 월 $200 수준이던 인프라를 t3.small 한 대로 낮췄지만, 항상 켜 두면 여전히 월 약 $24
(인스턴스 약 $19, 공인 IPv4 약 $3.6, gp3 20GB 약 $1.8)가 든다. 이 서버의 용도는 포트폴리오 시연이라
사람이 보는 시간은 대부분 평일 낮이다. 비용 때문에 2026-09-17에 손으로 꺼 두었더니, 그 뒤 main에 머지된
변경마다 backend-cd가 `InvalidInstanceId`(꺼진 인스턴스에는 SSM 명령을 보낼 수 없음)로 실패했다.
새벽 배치(cron)도 돌지 않았고, 프론트는 원인을 알 수 없는 오류만 보였다.

## 결정

평일 09:00에 켜고 매일 18:00(KST)에 끈다. 꺼져 있는 시간에 생기는 일은 아래처럼 처리한다.

| 대상 | 처리 |
| --- | --- |
| 켜고 끄기 | EventBridge Scheduler 두 개(`infra/terraform/modules/ec2/schedule.tf`). 시작은 월~금 09:00, 중지는 매일 18:00 — 주말·저녁에 손으로 켠 경우도 그날 18:00에 꺼진다. 스케줄러 역할은 이 인스턴스의 시작·중지만 할 수 있다 |
| 배포 | backend-cd가 인스턴스 상태를 본다(`.github/scripts/deploy-ec2.sh`). 꺼져 있으면 이미지만 올리고 배포를 건너뛴 채 경고와 함께 성공한다. 켜져 있으면 SSM 에이전트가 붙기를 최대 3분 기다려 배포한다 |
| 밀린 배포 | 부팅 작업(`infra/compose/host/boot.sh`, systemd `jbank-boot.service`)이 `:latest` 이미지의 `org.opencontainers.image.revision` 라벨을 보고, 현재 태그와 다르면 그 revision으로 api를 교체한다 |
| 배치 | 새벽 cron을 없애고 부팅 작업이 켜진 직후 돌린다. 만기이자(오늘), CTR·FDS(마지막 완료 기준일 다음 날부터 어제까지 하루씩 따라잡기), 원장 대사 |
| 화면 | 프론트 프록시가 연결 단계 오류를 `503 SERVER_OFFLINE`으로 구분하고, `/api/server-status`가 백엔드에 닿는지 확인해 꺼져 있으면 "평일 09:00~18:00(KST)에만 운영" 안내를 띄운다. 판단 기준은 시계가 아니라 실제 연결이다 |

CD와 부팅 작업이 동시에 배포하지 않도록, 둘 다 `/var/lib/jbank/deploy.lock`을 잡은 상태에서 저장소 갱신과
배포를 한다. 잠금은 호출하는 쪽이 잡고 `deploy.sh` 안에서는 잡지 않는다(중첩하면 교착). 손으로 배포할 때도
같은 형태로 한다.

```bash
runuser -u ec2-user -- flock /var/lib/jbank/deploy.lock bash -c \
  'cd /opt/jbank && git fetch -q origin main && git reset -q --hard origin/main && IMAGE_TAG=<전체 sha> bash infra/compose/deploy.sh'
```

부팅 스크립트는 저장소 밖 `/usr/local/lib/jbank`에 설치한다(`infra/compose/host/install-host.sh`). 실행 중에
`git reset`이 스크립트를 바꾸지 않게 하기 위해서다. backend-cd가 배포할 때마다 설치 스크립트를 다시 실행하고,
새 인스턴스는 user_data가 실행한다.

## 결과

- 비용: 월 약 $24에서 약 $11(인스턴스 약 $6 + IPv4·디스크 약 $5.4)로 준다.
- 공휴일에도 평일이면 켜진다(하루 약 $0.24). 스케줄러는 공휴일을 모른다.
- 꺼진 동안 Prometheus·Grafana도 꺼져 지표에 매일 공백이 생긴다.
- 09:00 직후 api가 준비되기 전(수십 초~수 분)에는 caddy 502가 나서 안내 대신 일반 오류가 보인다. 서버는 응답하고 있으므로 "꺼짐"으로 보지 않는다.
- 손으로 IMAGE_TAG를 고정해 롤백해도, 다음 부팅 때 `:latest`의 revision으로 다시 바뀐다. 롤백을 유지하려면 롤백 커밋을 main에 올린다.
- 18:00 중지와 겹친 배포는 실패로 끝난다. 다음 부팅이 `:latest`를 반영하므로 다시 돌릴 필요는 없다.
- 전환일 한계: cron 시절 CTR·FDS는 runDate=당일로 새벽에 돌아 그날 새벽분만 검사했다. 따라잡기는 마지막 완료 기준일 다음 날부터라 그 이전 날짜의 나머지 시간대는 채우지 않는다.
- 부팅 작업이 실패해도(api 준비 10분 초과, 배치 실패) 알림은 없고 `/var/log/jbank/boot.log`·`systemctl status jbank-boot`에만 남는다.
- 새 인스턴스는 `.env`를 채우기 전 첫 재부팅에서 api가 뜨지 않아 부팅 작업이 실패로 남는다(의도된 동작).
- 적용 순서: 이 결정을 도입할 때는 terraform apply(스케줄러·배포 역할 권한)를 먼저 하고, PR은 운영 시간 중에 머지한다. 새 backend-cd가 배포와 함께 호스트 설치를 하기 때문이다. 운영 시간 밖에 머지했다면 다음 부팅 전에 SSM으로 `bash /opt/jbank/infra/compose/host/install-host.sh`를 한 번 실행한다.
