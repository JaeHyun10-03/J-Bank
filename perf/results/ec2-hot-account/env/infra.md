# perf 인프라 생성 조건 (REQ-01)

- 기록 시각: 2026-09-28T15:37:56.733804
- target: i-0058c69c1bfbd2e51 t3.small AZ=ap-northeast-2d private=172.31.58.46 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- loadgen: i-037245606d1f20d42 c7i.large AZ=ap-northeast-2d private=172.31.63.179 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- 대상 CPU 크레딧: unlimited
- SSM i-0058c69c1bfbd2e51: Online
- SSM i-037245606d1f20d42: Online
- SG jbank-perf-target 인바운드 tcp 9091 ← sg-04676f650f485c90f
- SG jbank-perf-target 인바운드 tcp 9187 ← sg-04676f650f485c90f
- SG jbank-perf-target 인바운드 tcp 443 ← sg-04676f650f485c90f
- SG jbank-perf-target 인바운드 tcp 9100 ← sg-04676f650f485c90f
- SG jbank-perf-target 인바운드 tcp 8081 ← sg-04676f650f485c90f
- 0.0.0.0/0 인바운드: 0건
- 인스턴스 역할 관리 정책: ['arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'], 인라인 정책: []
