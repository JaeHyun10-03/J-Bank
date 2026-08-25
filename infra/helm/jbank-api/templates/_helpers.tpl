{{- define "jbank-api.fullname" -}}
{{ .Release.Name }}-jbank-api
{{- end -}}

{{- define "jbank-api.labels" -}}
app.kubernetes.io/name: jbank-api
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
