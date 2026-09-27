# __TARGET_IP__는 perf/run-ec2.sh setup-loadgen이 대상 사설 IP로 바꾼다.
global:
  scrape_interval: 5s

scrape_configs:
  # /actuator/prometheus는 인증이 필요해 대상의 metrics-proxy(perf 전용 계정 로그인)를 거친다.
  - job_name: api
    metrics_path: /metrics
    static_configs:
      - targets: ["__TARGET_IP__:9091"]
  - job_name: target-node
    static_configs:
      - targets: ["__TARGET_IP__:9100"]
  - job_name: target-cadvisor
    static_configs:
      - targets: ["__TARGET_IP__:8081"]
  - job_name: target-postgres
    static_configs:
      - targets: ["__TARGET_IP__:9187"]
  - job_name: loadgen-node
    static_configs:
      - targets: ["node-exporter:9100"]
