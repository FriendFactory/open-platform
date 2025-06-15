#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail
if [[ "${TRACE-0}" == "1" ]]; then
    set -o xtrace
fi

ENV=${1:-dev}

envs=("dev" "prod" "ixia-prod")

if [[ ! " ${envs[*]} " =~  ${ENV}  ]]; then
    echo "Environment name must be one of 'dev' 'prod' 'ixia-prod'"
    exit 1
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "${CURRENT_BRANCH}" != "frever-env-ml" && "${ENV}" == "prod" ]]; then
    echo "When releasing to prod, the branch must be 'frever-env-ml'"
    exit 1
fi

declare -A ECS_SERVICE
ECS_SERVICE[dev]="ml-service-dev"
ECS_SERVICE[prod]="ml-service"
ECS_SERVICE[ixia-prod]="ml-service-ixia-prod"

QUARKUS_ENV=${ENV}
if [[ "${ENV}" == "ixia-prod" ]]; then
    QUARKUS_ENV="prod"
fi

./mvnw package -Dquarkus.container-image.tag="${ENV}" -Dquarkus.profile="${QUARKUS_ENV}"
aws --profile frever-machine-learning --region eu-central-1 ecr get-login-password | docker login --username AWS --password-stdin 304552489232.dkr.ecr.eu-central-1.amazonaws.com
docker push 304552489232.dkr.ecr.eu-central-1.amazonaws.com/ml-service:"${ENV}"
aws --profile frever-machine-learning --region eu-central-1 ecs update-service --force-new-deployment --service "${ECS_SERVICE[$ENV]}" --cluster machine-learning --no-paginate

