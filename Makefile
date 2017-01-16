.DEFAULT_GOAL = help

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

.PHONY: dev-server
dev-server: dev-server-clean
	docker-compose -f docker-compose.yml build fortnight
	docker-compose -f docker-compose.yml up fortnight

.PHONY: dev-server-clean
dev-server-clean:
	docker rmi fortnight || :
	docker-compose -f docker-compose.yml kill fortnight || :
	docker-compose -f docker-compose.yml rm -f fortnight || :