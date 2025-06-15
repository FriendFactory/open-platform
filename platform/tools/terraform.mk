REGION ?= "eu-central-1"
S3_BACKEND_CONFIG_FILE ?= "workspace-configs/$(ENVIRONMENT)-$(REGION).tfvars"

PLANFILE = "$(ENVIRONMENT)-$(PROJECT).plan"
STATEFILE = $(PROJECT)/$(REGION).tfstate
AWS_PROFILE ?= "frever"

UNAME_S ?= $(shell uname -s)
STAT_COMMAND = "$(shell if [ "$(UNAME_S)" = 'Linux' ]; then echo "stat -c '%Y'"; else echo "/usr/bin/stat -f'%m'"; fi)"

init-backend:
	@rm -f .terraform/terraform.tfstate
	@terraform init --force-copy --backend-config="key=$(STATEFILE)" --backend-config="profile=$(AWS_PROFILE)" --backend-config="region=$(REGION)" $(UPGRADE)
	@terraform get -update=true

init: init-backend
	@if [ "$(ENVIRONMENT)" != "$(shell terraform workspace show)" ]; then terraform workspace select "$(ENVIRONMENT)" || terraform workspace new "$(ENVIRONMENT)"; fi

upgrade: UPGRADE="-upgrade"
upgrade: init

plan:: init
	@rm -f "$(PLANFILE)"
	@terraform plan -input=false -refresh=true $(PLAN_ARGS) -out="$(PLANFILE)"

plan-destroy: init
	@terraform plan -destroy -input=false -refresh=true $(PLAN_ARGS) -out="$(PLANFILE)"

show: init
	@terraform show

apply:: init
	@terraform apply -input=true -refresh=true "$(PLANFILE)"

