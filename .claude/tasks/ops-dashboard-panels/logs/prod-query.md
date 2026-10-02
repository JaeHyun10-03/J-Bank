# AC-02 / REQ-02~05·07 / OPS-07 운영 Prometheus 쿼리 검증 (병합 전)

- 대상: 운영 EC2 i-0b71df18394cf8010 Prometheus(`localhost:9090`, SSM Run Command, 읽기 전용)
- 쿼리: 변경된 대시보드 JSON의 새 패널(6~13) 식에서 `$__rate_interval`만 `5m`으로 바꿔 그대로 실행. 쿼리 URL은 대시보드 JSON에서 생성(python `urllib.parse.quote`).
- 외부 요청(로컬 curl, 미인증 GET만, 운영 데이터 변경 없음): 01:34:35Z~01:36:07Z
  - `GET https://api.j-bank.site/api/v1/products` 5초 간격 18회
  - `GET https://api.j-bank.site/actuator/health` 3회, `GET https://api.j-bank.site/api/v1/no-such-path` 3회(30초 간격)
- 조회 시각: 마지막 요청 뒤 약 45초(01:36:52Z), `[5m]` 창 안

## 외부 요청 실제 응답

| 요청 | 횟수 | status |
| --- | --- | --- |
| `actuator/health` | 3 | 404 |
| `no-such-path` | 3 | 401 |
| `products` | 18 | 200 |

## 카운터 전후 (`sum by (uri,status) (http_server_requests_seconds_count{job="jbank-api",uri=~"/\\*\\*|UNKNOWN|/api/v1/products"})`)

- 전: SSM 00c2fc14-582f-4b37-8b08-6cce5eedba79 (01:34:26Z)
```
{"status":"success","data":{"resultType":"vector","result":[{"metric":{"status":"401","uri":"UNKNOWN"},"value":[1790904864.656,"68"]},{"metric":{"status":"404","uri":"/**"},"value":[1790904864.656,"1"]}]}}
```
- 후: SSM 9b5406dd-a516-4c53-840b-8582805ef7c8 (01:36:52Z)
```
{"status":"success","data":{"resultType":"vector","result":[{"metric":{"status":"401","uri":"UNKNOWN"},"value":[1790905005.586,"71"]},{"metric":{"status":"404","uri":"/**"},"value":[1790905005.586,"4"]},{"metric":{"status":"200","uri":"/api/v1/products"},"value":[1790905005.586,"18"]}]}}
```
- `uri="/**",status="404"` 1 → 4(+3): `/actuator/health` 3회가 uri `/**`로 기록됨을 직접 확인(계획 리뷰 3회차 권고-B).
- `uri="UNKNOWN",status="401"` 68 → 71(+3): `/api/v1/no-such-path` 3회. 68은 그 전 외부 탐색 요청.
- `uri="/api/v1/products",status="200"` 없음 → 18.

## 새 패널 쿼리 결과 (SSM 9b5406dd-a516-4c53-840b-8582805ef7c8)

```
== panel 6 입금 반영 대기 수 / 미반영
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905005.776,"0"]}]}}
== panel 7 가장 오래된 미반영 입금 나이 / 나이
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905005.988,"0"]}]}}
== panel 8 입금 반영 지연 (생성→반영) / p95
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905006.166,"NaN"]}]}}
== panel 8 입금 반영 지연 (생성→반영) / max
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905006.335,"0"]}]}}
== panel 9 API별 p95 / {{method}} {{uri}}
{"status":"success","data":{"resultType":"vector","result":[{"metric":{"method":"GET","uri":"/api/v1/products"},"value":[1790905006.525,"0.0073400312499999985"]}]}}
== panel 10 전체 API 오류율 (5xx·4xx) / 5xx
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905006.696,"0"]}]}}
== panel 10 전체 API 오류율 (5xx·4xx) / 4xx
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905006.855,"0.2828366750781115"]}]}}
== panel 11 JVM 힙 / used
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905007.026,"70520720"]}]}}
== panel 11 JVM 힙 / max
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905007.173,"536870910"]}]}}
== panel 12 GC 멈춤 시간 비율 / GC 멈춤
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905007.365,"0.00006666666666666662"]}]}}
== panel 13 GC 최대 멈춤 / max
{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1790905007.557,"0.019"]}]}}

```

## 판정

| 요구사항 | 기대 | 결과 | 판정 |
| --- | --- | --- | --- |
| 전체 | 모든 새 쿼리 `success` | 11개 모두 success | 충족 |
| REQ-02 | 건수·나이·max 숫자, p95는 이체 없으면 NaN 허용 | 건수 0, 나이 0, max 0, p95 NaN(오늘 이체 없음) | 충족 |
| REQ-03·07 | p95에 `GET /api/v1/products` 숫자 계열만, `UNKNOWN`·`/**`·상태 확인 경로 없음 | `{method="GET",uri="/api/v1/products"}` 0.0073초 단일 계열. 같은 창에 `UNKNOWN`·`/**` 요청이 있었지만 결과에 없음 | 충족 |
| REQ-05·07 | 5xx 단일 계열 0, 4xx 단일 계열 0 초과 | 5xx 0, 4xx 0.283 | 충족 |
| REQ-04 | 힙 used/max·GC 비율·최대 멈춤 숫자 | used 70,520,720B, max 536,870,910B(512MiB, G1 Eden·Survivor max -1 합산으로 2바이트 작음), GC 비율 0.0000667, 최대 멈춤 0.019초 | 충족 |

한계: 5xx는 운영에서 안전하게 만들 수 없어 값 0까지만 확인(OPS-07 행). 반영 지연 p95는 이체가 생긴 뒤에야 숫자가 나온다.

## 쿼리 생성 명령 (결과 리뷰 1회차 권고-2)

대시보드 JSON에서 새 패널(id 6~13, 행 제외) 식을 읽어 `$__rate_interval`만 `5m`으로 바꾸고 URL 인코딩해 SSM 명령을 만들었다.

```python
d = json.load(open('infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json', encoding='utf-8'))
for p in d['panels']:
    if p['id'] >= 6 and p['type'] != 'row':
        for t in p['targets']:
            q = t['expr'].replace('$__rate_interval', '5m')
            cmd = "$P 'http://localhost:9090/api/v1/query?query=%s'" % urllib.parse.quote(q, safe='')
# $P = docker compose -f docker-compose.prod.yml exec -T prometheus wget -qO-  (/opt/jbank/infra/compose에서)
```
