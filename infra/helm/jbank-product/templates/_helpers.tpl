{{- define "jbank-product.fullname" -}}
{{ .Release.Name }}-jbank-product
{{- end -}}

{{- define "jbank-product.labels" -}}
app.kubernetes.io/name: jbank-product
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
