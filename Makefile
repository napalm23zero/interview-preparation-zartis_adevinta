COMPOSE = docker compose --env-file .env-dev
ALL_PROFILES = --profile apps --profile mobile

.PHONY: init resources config check up up-build app-up app-stop stop logs
init:
	@sh scripts/initialize.sh

resources: init
	@sh scripts/resources.sh

config: init
	@$(COMPOSE) $(ALL_PROFILES) config --quiet

check: config
	@bash tests/resources-test.sh

up: resources
	@$(COMPOSE) up -d

up-build: resources
	@$(COMPOSE) up -d --build

app-up: resources
	@$(COMPOSE) --profile apps up -d --build nginx backend frontend ai-gateway

app-stop: init
	@$(COMPOSE) --profile apps stop nginx backend frontend ai-gateway

stop: init
	@$(COMPOSE) $(ALL_PROFILES) stop

logs: init
	@$(COMPOSE) $(ALL_PROFILES) logs -f
