#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail
if [[ "${TRACE-0}" == "1" ]]; then
    set -o xtrace
fi

ENV=${1:-dev}

envs=("dev" "test" "stage" "prod" "ixia-prod")

if [[ ! " ${envs[*]} " =~  ${ENV}  ]]; then
    echo "Environment name must be one of 'dev' 'test' 'stage' 'prod' ixia-prod'"
    exit 1
fi

mvn clean package -Dquarkus.container-image.tag="${ENV}" -Dquarkus.profile="${ENV}"
aws --region eu-central-1 ecr get-login-password | docker login --username AWS --password-stdin 722913253728.dkr.ecr.eu-central-1.amazonaws.com
docker push 722913253728.dkr.ecr.eu-central-1.amazonaws.com/timers:"${ENV}"
aws --region eu-central-1 ecs update-service --force-new-deployment --service timers --cluster "${ENV}" --no-paginate