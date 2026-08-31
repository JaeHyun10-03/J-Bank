apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ${name}
  namespace: ${argocd_namespace}
spec:
  project: default
  source:
    repoURL: ${repo_url}
    targetRevision: ${target_revision}
    path: ${chart_path}
    helm:
      valueFiles:
        - values.yaml
        - ${values_file}
        - values-image.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: ${namespace}
%{ if automated_sync ~}
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
%{ else ~}
  syncPolicy:
    syncOptions:
      - CreateNamespace=true
%{ endif ~}
