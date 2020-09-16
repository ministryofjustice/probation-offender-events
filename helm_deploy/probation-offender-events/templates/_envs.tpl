    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: SERVER_PORT
    value: "{{ .Values.image.port }}"

  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "logstash"

  - name: APPLICATION_INSIGHTS_IKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: OAUTH_ENDPOINT_URL
    value: "{{ .Values.env.OAUTH_ENDPOINT_URL }}"

  - name: COMMUNITY_ENDPOINT_URL
    value: "{{ .Values.env.COMMUNITY_ENDPOINT_URL }}"

  - name: OFFENDERUPDATEPOLL_ENABLED
    value: "{{ .Values.env.OFFENDERUPDATEPOLL_ENABLED }}"

  - name: OAUTH_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OAUTH_CLIENT_ID

  - name: OAUTH_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: OAUTH_CLIENT_SECRET

  - name: SNS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: probation-offender-events-topic
        key: access_key_id

  - name: SNS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: probation-offender-events-topic
        key: secret_access_key

  - name: SNS_TOPIC_ARN
    valueFrom:
      secretKeyRef:
        name: probation-offender-events-topic
        key: topic_arn

{{- end -}}
