# 운영 사전 측정 (계획 근거, OPS-04·06)

계획 단계에서 progress.md 부록에 남긴 원본을 그대로 옮겼다.


SSM 명령 ID는 실행 당시 출력하지 않아 남기지 못함.

### 1. 유휴 메모리 (2026-10-29T07:39:35Z, 부팅 배치 종료 후)

명령: `date -u; uptime; free -m; grep -E "MemAvailable|SwapTotal" /proc/meminfo; docker stats --no-stream --format "{{.Name}} {{.MemUsage}} {{.CPUPerc}}"; df -h /`

```text
Thu Oct 29 07:39:35 UTC 2026
 07:39:35 up  7:38,  0 users,  load average: 0.08, 0.09, 0.02
               total        used        free      shared  buff/cache   available
Mem:            1913         803         369          16         741         944
Swap:              0           0           0
MemAvailable:     966908 kB
SwapTotal:             0 kB
compose-api-1 502MiB / 1.869GiB 0.36%
compose-caddy-1 27.1MiB / 1.869GiB 0.00%
compose-redis-1 9.688MiB / 1.869GiB 0.29%
compose-postgres-1 63.41MiB / 1.869GiB 0.12%
compose-grafana-1 101.3MiB / 1.869GiB 0.06%
compose-prometheus-1 44.3MiB / 1.869GiB 0.00%
Filesystem      Size  Used Avail Use% Mounted on
/dev/nvme0n1p1   20G  4.8G   16G  25% /
```

### 2. compose 버전

명령: `docker compose version; docker version --format {{.Server.Version}}`

```text
Docker Compose version v2.29.7
25.0.16
```

### 3. 부팅 직후 api 수집 실패 구간

명령(날짜별): `docker exec compose-prometheus-1 wget -qO- "http://localhost:9090/api/v1/query_range?query=up&start=<날짜>T00:00:00Z&end=<날짜>T00:10:00Z&step=15s"`
원본 JSON을 요약(첫 표본 시각·값, 처음 up=1 시각, 0 표본 수):

```text
2026-10-26 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
2026-10-27 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
2026-10-28 first sample 00:01:30 0 first up=1 00:02:00 zero samples 2
2026-10-29 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
```

Prometheus도 부팅 때 함께 시작하므로 첫 표본 이전은 관측되지 않는다. `journalctl -u jbank-boot`: 부팅 작업은 00:00:57~00:01:04에 시작해 2~3.5분 뒤 종료.
