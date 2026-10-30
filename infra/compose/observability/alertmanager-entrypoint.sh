#!/bin/sh
# Alertmanager 설정은 환경변수 치환을 못 하므로 기동할 때 만든다. 받는 주소·SMTP 계정은 저장소에 두지 않고
# .env.alertmanager(운영)나 compose 환경변수(로컬)로 받는다(ADR 0014).
# ALERT_EMAIL_TO·SMTP_SMARTHOST·SMTP_FROM 중 하나라도 비면 경고를 남기고 메일 없이 기동한다 —
# 알림은 Alertmanager·Grafana에서 계속 보인다. 값이 틀리면 정상 기동하고 발송할 때 실패가 로그에 남는다.
set -eu

CONF=/tmp/alertmanager.yml
PASS_FILE=/tmp/smtp_password

cat > "$CONF" <<EOF
route:
  receiver: mail
  group_by: [alertname, target]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 12h
EOF

if [ -n "${ALERT_EMAIL_TO:-}" ] && [ -n "${SMTP_SMARTHOST:-}" ] && [ -n "${SMTP_FROM:-}" ]; then
  # 비밀번호는 YAML 따옴표 처리를 피하려고 파일로 넘긴다.
  printf '%s' "${SMTP_AUTH_PASSWORD:-}" > "$PASS_FILE"
  cat >> "$CONF" <<EOF
receivers:
  - name: mail
    email_configs:
      - to: '${ALERT_EMAIL_TO}'
        from: '${SMTP_FROM}'
        smarthost: '${SMTP_SMARTHOST}'
        require_tls: ${SMTP_REQUIRE_TLS:-true}
        send_resolved: true
EOF
  if [ -n "${SMTP_AUTH_USERNAME:-}" ]; then
    cat >> "$CONF" <<EOF
        auth_username: '${SMTP_AUTH_USERNAME}'
        auth_password_file: '${PASS_FILE}'
EOF
  fi
else
  echo "alertmanager-entrypoint: 경고 — ALERT_EMAIL_TO·SMTP_SMARTHOST·SMTP_FROM 중 비어 있는 값이 있어 메일 없이 기동합니다" >&2
  cat >> "$CONF" <<EOF
receivers:
  - name: mail
EOF
fi

exec "$@"
