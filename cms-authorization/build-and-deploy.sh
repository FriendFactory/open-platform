#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail
if [[ "${TRACE-0}" == "1" ]]; then
    set -o xtrace
fi

GIT_COMMIT=$(git show -s --format=%H)
./mvnw clean package -Dquarkus.container-image.tag="$GIT_COMMIT"
aws --region eu-central-1 ecr get-login-password | docker login --username AWS --password-stdin 722913253728.dkr.ecr.eu-central-1.amazonaws.com
docker push 722913253728.dkr.ecr.eu-central-1.amazonaws.com/cms-authorization:"$GIT_COMMIT"
kubectl config use-context arn:aws:eks:eu-central-1:722913253728:cluster/content-stage
helm upgrade --namespace cms --set app.gitCommit="$GIT_COMMIT" --set app.imageTag="$GIT_COMMIT" --description "Commit ${GIT_COMMIT}" --install cms-authorization ./helm/cms-authorization
