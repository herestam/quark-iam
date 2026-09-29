# =====================================================================
# Build, test and publish the container image to Docker Hub.
#
#   make push                      build + tag + push (tag = git sha)
#   make push-latest               same but also move the :latest tag
#   make push-multiarch            one image for amd64 and arm64
#
# Only the coordinates at the top need editing.
# =====================================================================

# --- image coordinates ----------------------------------------------------
DOCKERHUB_USER ?= dxtech2024
IMAGE_NAME     ?= email-campaign-manager
REGISTRY       ?= docker.io
PLATFORMS      ?= linux/amd64,linux/arm64

IMAGE      := $(REGISTRY)/$(DOCKERHUB_USER)/$(IMAGE_NAME)
GIT_SHA    := $(shell git rev-parse --short HEAD 2>/dev/null || echo nogit)
IMAGE_TAG  ?= $(GIT_SHA)

# Quarkus serves on 8080 inside the container; override to change the host port.
HOST_PORT   ?= 8080
CONTAINER   ?= email-app

DOCKER      := docker
MAVEN       := ./mvnw
.DEFAULT_GOAL := help

.PHONY: help login logout build test package run push push-latest push-multiarch \
        verify dev up down logs ps clean distclean version

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk -F':.*?## ' '{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

version: ## Print the image coordinates this run would publish
	@echo "image : $(IMAGE):$(IMAGE_TAG)"

# --- local build ----------------------------------------------------------

package: ## Build the Quarkus fast-jar locally
	$(MAVEN) clean package -DskipTests

test: ## Run the unit test suite
	$(MAVEN) clean test

build: ## Build the image for the current architecture
	$(DOCKER) build -t $(IMAGE):$(IMAGE_TAG) .

verify: build ## Start the image and wait until it reports ready
	@echo "starting $(IMAGE):$(IMAGE_TAG) on port $(HOST_PORT)"
	@$(DOCKER) run -d --rm --name $(CONTAINER)-verify -p $(HOST_PORT):8080 \
		$(IMAGE):$(IMAGE_TAG) >/dev/null
	@for i in $$(seq 1 40); do \
		if $(DOCKER) run --rm --network host curlimages/curl:latest -sf \
			http://localhost:$(HOST_PORT)/q/health/ready >/dev/null 2>&1; then \
			echo "healthy after $((i*3))s"; \
			$(DOCKER) stop $(CONTAINER)-verify >/dev/null; exit 0; \
		fi; \
		sleep 3; \
	done; \
	echo "FAILED: /q/health/ready never returned 200"; \
	$(DOCKER) logs --tail 60 $(CONTAINER)-verify; \
	$(DOCKER) stop $(CONTAINER)-verify >/dev/null; \
	exit 1

# --- publish --------------------------------------------------------------

login: ## docker login (expects DOCKERHUB_USER / DOCKERHUB_TOKEN in the environment)
	@test -n "$(DOCKERHUB_USER)" || { echo "set DOCKERHUB_USER"; exit 1; }
	@test -n "$(DOCKERHUB_TOKEN)" || { echo "set DOCKERHUB_TOKEN"; exit 1; }
	@echo "$(DOCKERHUB_TOKEN)" | $(DOCKER) login -u $(DOCKERHUB_USER) --password-stdin

logout: ## docker logout
	-$(DOCKER) logout

push: ## Build, tag and push IMAGE:IMAGE_TAG
	$(DOCKER) build -t $(IMAGE):$(IMAGE_TAG) .
	$(DOCKER) push $(IMAGE):$(IMAGE_TAG)
	@echo "pushed $(IMAGE):$(IMAGE_TAG)"

push-latest: ## Push IMAGE:IMAGE_TAG and move the :latest tag with it
	$(MAKE) push
	$(DOCKER) tag $(IMAGE):$(IMAGE_TAG) $(IMAGE):latest
	$(DOCKER) push $(IMAGE):latest
	@echo "pushed $(IMAGE):latest"

push-multiarch: ## Push one multi-platform image to IMAGE:IMAGE_TAG
	$(DOCKER) buildx create --use --name emailapp-builder 2>/dev/null || true
	$(DOCKER) buildx build --platform $(PLATFORMS) \
		-t $(IMAGE):$(IMAGE_TAG) -t $(IMAGE):latest --push .

# --- local stack ----------------------------------------------------------

dev: ## Run the app in dev mode against the database in .env
	$(MAVEN) quarkus:dev

up: ## Start the local compose stack (builds the image from source)
	$(DOCKER) compose up -d --build
	@echo "app      http://localhost:$(HOST_PORT)"
	@echo "dev ui   http://localhost:$(HOST_PORT)/q/dev-ui"

down: ## Stop the local compose stack
	$(DOCKER) compose down

logs: ## Tail the local compose logs
	$(DOCKER) compose logs -f --tail 100

ps: ## Show the local compose status
	$(DOCKER) compose ps

# --- housekeeping ---------------------------------------------------------

clean: ## Remove build output
	$(MAVEN) clean || true

distclean: ## Remove build output, local containers and volumes
	$(MAVEN) clean || true
	-$(DOCKER) rm -f $(CONTAINER) $(CONTAINER)-verify 2>/dev/null
	-$(DOCKER) compose down -v 2>/dev/null
