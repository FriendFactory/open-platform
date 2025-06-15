# Frever Observability tools setup in K8S

## Promtail Heml setup

helm repo add loki https://grafana.github.io/loki/charts

kubectl create namespace monitoring

### For dev-1 env

sed -E 's/LOKI_URL/dev-1-observability.frever-internal.com/' promtail-values.yaml | helm install promtail --namespace monitoring grafana/promtail -f -

### For content-stage env

sed -E 's/LOKI_URL/content-stage-observability.frever-internal.com/' promtail-values.yaml | helm install promtail --namespace monitoring grafana/promtail -f -

## Prometheus service account setup

kubectl apply -f prometheus-setup-in-k8s.yml

kubectl apply -f kube-state-metrics-setup.yml

### There is a Ansible playbook(setup-observability.yml) in ../playbooks folder to setup Prometheus.

### Dashboards in "grafana-dashboards" folder could be imported into Grafana using "/dashboard/import" URL.

