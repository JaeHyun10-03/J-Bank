# 설정 검사 (완료 기준 1·4 일부·5 일부, OPS-01, SEC-07, REQ-02)

실행 스크립트: 세션 스크래치 `config-check.sh`(아래에 각 명령을 그대로 출력). 운영 compose는 저장소 밖 임시 폴더에 복사하고 더미 `.env`(DB_PASSWORD에 / + = @ 포함)로 검사했다. 두 번째 `config --services`는 `COMPOSE_PROFILES=logs` 환경에서 실행한 것이다. Loki `-verify-config`는 출력 없이 종료 코드 0이다.

```text
## 시각: 2026-10-29T10:35:54Z, 커밋: 5419093

$ docker compose -f /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml --profile core --profile api --profile observability config -q
exit=0

$ docker compose -f /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/prodcheck/docker-compose.prod.yml config -q
exit=0

# prod 더미 .env의 DB_PASSWORD에는 특수문자(/ + = @)가 들어 있다
$ docker compose -f /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/prodcheck/docker-compose.prod.yml config --services
prometheus
redis
alertmanager
postgres
api
grafana
caddy
postgres-exporter
exit=0

$ docker compose -f /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/prodcheck/docker-compose.prod.yml config --services
loki
postgres-exporter
prometheus
socket-proxy
alloy
postgres
redis
api
alertmanager
grafana
caddy
exit=0

$ docker run --name chk1 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability:/w --entrypoint promtool prom/prometheus:v2.55.1 check config /w/prometheus.yml
Checking /w/prometheus.yml
 SUCCESS: /w/prometheus.yml is valid prometheus config file syntax

exit=0

$ docker run --name chk2 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability:/w --entrypoint promtool prom/prometheus:v2.55.1 check config /w/prometheus.prod.yml
Checking /w/prometheus.prod.yml
 SUCCESS: /w/prometheus.prod.yml is valid prometheus config file syntax

exit=0

$ docker run --name chk3 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability:/w --entrypoint promtool prom/prometheus:v2.55.1 check rules /w/rules/alerts.yml
Checking /w/rules/alerts.yml
  SUCCESS: 4 rules found

exit=0

# amtool: 메일 변수 있음(Gmail 형태 더미 값)
$ docker run --name chk4 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/alertmanager-entrypoint.sh:/e.sh:ro -e ALERT_EMAIL_TO=to@example.com -e SMTP_FROM=from@example.com -e SMTP_SMARTHOST=smtp.gmail.com:587 -e SMTP_AUTH_USERNAME=from@example.com -e SMTP_AUTH_PASSWORD=a'b c"d --entrypoint /bin/sh prom/alertmanager:v0.34.1 /e.sh amtool check-config /tmp/alertmanager.yml
Checking '/tmp/alertmanager.yml'  SUCCESS
Found:
 - global config
 - route
 - 0 inhibit rules
 - 1 receivers
 - 0 templates

exit=0

# amtool: 메일 변수 없음
$ docker run --name chk5 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/alertmanager-entrypoint.sh:/e.sh:ro --entrypoint /bin/sh prom/alertmanager:v0.34.1 /e.sh amtool check-config /tmp/alertmanager.yml
alertmanager-entrypoint: 경고 — ALERT_EMAIL_TO·SMTP_SMARTHOST·SMTP_FROM 중 비어 있는 값이 있어 메일 없이 기동합니다
Checking '/tmp/alertmanager.yml'  SUCCESS
Found:
 - global config
 - route
 - 0 inhibit rules
 - 1 receivers
 - 0 templates

exit=0

$ docker run --name chk6 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/loki.yml:/etc/loki/loki.yml:ro grafana/loki:3.7.8 -config.file=/etc/loki/loki.yml -verify-config
exit=0

$ docker run --name chk7 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/alloy.alloy:/c.alloy:ro grafana/alloy:v1.20.1 fmt /c.alloy
	}
}

loki.source.docker "containers" {
	host          = "tcp://socket-proxy:2375"
	targets       = discovery.docker.containers.targets
	relabel_rules = discovery.relabel.containers.rules
	forward_to    = [loki.write.local.receiver]
}

loki.write "local" {
	endpoint {
		url = "http://loki:3100/loki/api/v1/push"
	}
}
exit=0

# REQ-02 보관 설정 키
$ grep -nE retention_period|retention_enabled|delete_request_store /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/loki.yml
30:  retention_period: 360h
32:# retention_period만으로는 지워지지 않는다 — compactor가 retention을 켜야 삭제한다.
35:  retention_enabled: true
36:  delete_request_store: filesystem
exit=0

# 새 이미지 태그(고정 버전)
$ grep -nE image: (grafana/loki|grafana/alloy|wollomatic|prom/alertmanager|quay.io/prometheuscommunity|axllent) /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:65:    image: quay.io/prometheuscommunity/postgres-exporter:v0.15.0
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:85:    image: prom/alertmanager:v0.34.1
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:103:    image: axllent/mailpit:v1.31.4
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:121:    image: grafana/loki:3.7.8
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:135:    image: grafana/alloy:v1.20.1
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.yml:157:    image: wollomatic/socket-proxy:1.13.1
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml:77:    image: quay.io/prometheuscommunity/postgres-exporter:v0.15.0
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml:99:    image: prom/alertmanager:v0.34.1
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml:125:    image: grafana/loki:3.7.8
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml:137:    image: grafana/alloy:v1.20.1
/Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/docker-compose.prod.yml:159:    image: wollomatic/socket-proxy:1.13.1
exit=0

# SEC-07/REQ-11: 운영 compose 새 서비스의 ports·소켓 마운트
$ python3 - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/prodcheck/docker-compose.prod.yml
loki ports= None mem_limit= 201326592 restart= unless-stopped networks= ['default'] docker.sock= []
alloy ports= None mem_limit= 100663296 restart= unless-stopped networks= ['default', 'docker-api'] docker.sock= []
socket-proxy ports= None mem_limit= 16777216 restart= unless-stopped networks= ['docker-api'] docker.sock= ['/var/run/docker.sock']
postgres-exporter ports= None mem_limit= 33554432 restart= unless-stopped networks= ['default'] docker.sock= []
alertmanager ports= None mem_limit= 33554432 restart= unless-stopped networks= ['default'] docker.sock= []
socket-proxy 외 소켓 마운트: []
docker-api 네트워크 internal: True
exit=0

$ git diff --stat origin/main -- infra/compose/Caddyfile
exit=0

```

## 추가: alloy fmt 전체 출력 (결과 리뷰 1회차 권고 9, 2026-10-29T12:11:05Z, 커밋 c500c5b)

```text
$ docker run --name chk-alloy-fmt -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/observability/alloy.alloy:/c.alloy:ro grafana/alloy:v1.20.1 fmt /c.alloy
// 도커 컨테이너 로그를 Loki로 보낸다. 도커 API는 socket-proxy를 거쳐 조회·로그 읽기만 한다(ADR 0014).

discovery.docker "containers" {
	host = "tcp://socket-proxy:2375"
}

discovery.relabel "containers" {
	targets = []

	// compose 서비스 이름(api, postgres …)
	rule {
		source_labels = ["__meta_docker_container_label_com_docker_compose_service"]
		target_label  = "service"
	}

	// 컨테이너 이름(compose-api-1 …)
	rule {
		source_labels = ["__meta_docker_container_name"]
		regex         = "/(.*)"
		target_label  = "container"
	}
}

loki.source.docker "containers" {
	host          = "tcp://socket-proxy:2375"
	targets       = discovery.docker.containers.targets
	relabel_rules = discovery.relabel.containers.rules
	forward_to    = [loki.write.local.receiver]
}

loki.write "local" {
	endpoint {
		url = "http://loki:3100/loki/api/v1/push"
	}
}
exit=0
```

fmt는 설정을 파싱해 정렬된 형태(탭 들여쓰기)로 출력한다. 종료 코드 0은 문법 오류가 없다는 뜻이다. 저장소 파일은 공백 들여쓰기라 출력과 공백만 다르다.
