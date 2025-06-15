#!/usr/bin/env bash

set -euo pipefail

REPLICAS="${1:-2}"
CLUSTER="${2:-$(kubectl config current-context | awk -F '/' '{print $2}')}"
LB_NAME="${CLUSTER//-eks-cluster/}"
NAMESPACE="${3:-modulai}"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

API_IDENTIFIER=$(kubectl get namespaces | grep app | sort -r | head -1 | awk '{print $1}' | sed 's/app-//' | sed 's/-/./')

read -p "Setup modulai recsysapp with $REPLICAS replicas for cluster '$CLUSTER' in namespace '$NAMESPACE': " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
    sed -E "s/NAMESPACE_NAME/$NAMESPACE/" "$DIR"/recsysapp.yml | sed -E "s/CLUSTER_NAME/$CLUSTER/" | sed -E "s/REPLICAS/$REPLICAS/" | sed -E "s/API_IDENTIFIER/$API_IDENTIFIER/" | sed -E "s/LB_NAME/$LB_NAME/" | kubectl apply -f -
    if ! (kubectl -n modulai describe serviceaccounts modulai | grep -q "role-arn"); then
        kubectl annotate serviceaccount -n "$NAMESPACE" modulai "eks.amazonaws.com/role-arn=arn:aws:iam::722913253728:role/$CLUSTER-modulai"
    fi
    kubectl -n "$NAMESPACE" rollout restart deployment
then
   exit 1
fi

