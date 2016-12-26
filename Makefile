dev-server: dev-server-clean
    docker-compose -f docker-compose.yml build fortnight
    docker-compose -f docker-compose.yml up fortnight

dev-server-clean:
    docker rmi fortnight || :
    docker-compose -f docker-compose.yml kill fortnight || :
    docker-compose -f docker-compose.yml rm -f fortnight || :