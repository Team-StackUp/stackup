# Build stage
FROM gradle:8.12-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# curl 은 docker-compose 의 healthcheck (/actuator/health) 에서 사용.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /home/gradle/src/build/libs/stackup-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 38010
ENTRYPOINT ["java", "-jar", "app.jar"]
