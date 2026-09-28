# perf 인프라 생성 조건 (REQ-01)

- 기록 시각: 2026-09-28T09:35:18.961452
- loadgen: i-0857a66bb156b5a74 c7i.large AZ=ap-northeast-2d private=172.31.58.166 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- target: i-0ffc99128c8443b78 t3.small AZ=ap-northeast-2d private=172.31.51.62 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- 대상 CPU 크레딧: unlimited
- SSM i-0ffc99128c8443b78: Online
- SSM i-0857a66bb156b5a74: Online
- SG jbank-perf-target 인바운드 tcp 9091 ← sg-045df32cee5280dab
- SG jbank-perf-target 인바운드 tcp 9187 ← sg-045df32cee5280dab
- SG jbank-perf-target 인바운드 tcp 443 ← sg-045df32cee5280dab
- SG jbank-perf-target 인바운드 tcp 9100 ← sg-045df32cee5280dab
- SG jbank-perf-target 인바운드 tcp 8081 ← sg-045df32cee5280dab
- 0.0.0.0/0 인바운드: 0건
- 인스턴스 역할 관리 정책: ['arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'], 인라인 정책: []
