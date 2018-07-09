
FROM openjdk:10-jre-slim

WORKDIR /app

ADD build/install/server/ /app/

VOLUME /tmp

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["/app/bin/server"]



