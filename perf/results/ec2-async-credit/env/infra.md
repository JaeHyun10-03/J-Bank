# perf 인프라 생성 조건 (REQ-01)

- 기록 시각: 2026-09-30T21:55:30.324571
- target: i-04db6e6104ed1a50b t3.small AZ=ap-northeast-2d private=172.31.48.46 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- loadgen: i-0d0307987979c72bd c7i.large AZ=ap-northeast-2d private=172.31.53.53 profile=arn:aws:iam::489470371163:instance-profile/jbank-perf-instance
- 대상 CPU 크레딧: unlimited
- SSM i-04db6e6104ed1a50b: Online
- SSM i-0d0307987979c72bd: Online
- SG jbank-perf-target 인바운드 tcp 9091 ← sg-0acd20b4b2f2ca713
- SG jbank-perf-target 인바운드 tcp 9187 ← sg-0acd20b4b2f2ca713
- SG jbank-perf-target 인바운드 tcp 443 ← sg-0acd20b4b2f2ca713
- SG jbank-perf-target 인바운드 tcp 9100 ← sg-0acd20b4b2f2ca713
- SG jbank-perf-target 인바운드 tcp 8081 ← sg-0acd20b4b2f2ca713
- 0.0.0.0/0 인바운드: 0건
- 인스턴스 역할 관리 정책: ['arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore'], 인라인 정책: []
