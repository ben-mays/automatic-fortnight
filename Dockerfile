# Dockerfile for building a lein uberjar docker image

FROM clojure:lein-2.7.1

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY project.clj /usr/src/app/
RUN lein deps

COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" fortnight.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "fortnight.jar"]
CMD ["start"]
