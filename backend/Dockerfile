# Build stage
FROM gradle:8.12-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/stackup-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 40000
ENTRYPOINT ["java", "-Dserver.port=40000", "-jar", "app.jar"]
