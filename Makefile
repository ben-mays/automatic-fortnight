.DEFAULT_GOAL = help

.PHONY: help
help:
	@echo "Fortnight:\n\tdev-server: Builds the fortnight docker image and launches the docker container.\n\tdev-server-clean: Cleans up the docker images and containers for the fortnight project."	

.PHONY: dev-server
dev-server: dev-server-clean
	docker-compose -f docker-compose.yml build fortnight
	docker-compose -f docker-compose.yml up fortnight

.PHONY: dev-server-clean
dev-server-clean:
	docker rmi fortnight || :
	docker-compose -f docker-compose.yml kill fortnight || :
	docker-compose -f docker-compose.yml rm -f fortnight || :