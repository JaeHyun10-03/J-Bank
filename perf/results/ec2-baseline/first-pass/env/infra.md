# perf 인프라 생성 조건 (REQ-01)

- 기록 시각: 2026-09-27T23:15:13.179035
- loadgen: i-096e37176e24102ff c7i.large AZ=ap-northeast-2d private=172.31.56.6 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- target: i-062a05cde1f7675e4 t3.small AZ=ap-northeast-2d private=172.31.63.69 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- 대상 CPU 크레딧: unlimited
- SSM i-062a05cde1f7675e4: Online
- SSM i-096e37176e24102ff: Online
- SG jbank-perf-target 인바운드 tcp 9091 ← sg-04f38f9aaf72cdb58
- SG jbank-perf-target 인바운드 tcp 9187 ← sg-04f38f9aaf72cdb58
- SG jbank-perf-target 인바운드 tcp 443 ← sg-04f38f9aaf72cdb58
- SG jbank-perf-target 인바운드 tcp 9100 ← sg-04f38f9aaf72cdb58
- SG jbank-perf-target 인바운드 tcp 8081 ← sg-04f38f9aaf72cdb58
- 0.0.0.0/0 인바운드: 0건
- 인스턴스 역할 관리 정책: ['arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'], 인라인 정책: []
