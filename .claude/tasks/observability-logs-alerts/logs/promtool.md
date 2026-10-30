# promtool 단위 테스트 (완료 기준 2, REQ-04~07)

시각: 2026-10-29T10:36:16Z, 커밋: 5419093

```text
$ docker run --name chk-promtool -v "$PWD/infra/compose/observability:/w" --entrypoint promtool prom/prometheus:v2.55.1 test rules /w/tests/alerts.test.yml
  SUCCESS

exit=0
```

사례: 교착 증가 0(없음)/1(발생), 5xx 4.9%(없음)/5%(발생)/4xx만(없음)/요청 0(없음), 다운 api·postgres-exporter·pg_up 1분45초(없음)/2분(3건 발생, target별), 모두 정상(없음), 락 대기 1초(없음)/1.01초(발생).
