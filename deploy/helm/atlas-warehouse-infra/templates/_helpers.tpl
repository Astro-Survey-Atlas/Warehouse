{{- define "atlas-warehouse-infra.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "atlas-warehouse-infra.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "atlas-warehouse-infra.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "atlas-warehouse-infra.labels" -}}
app.kubernetes.io/name: {{ include "atlas-warehouse-infra.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
