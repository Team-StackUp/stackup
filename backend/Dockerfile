# Build stage
#
# 베이스 이미지에 이미 들어있는 Gradle 을 쓰고, wrapper 로 배포판을 내려받지 않는다.
# self-hosted runner 에서 services.gradle.org 접속이 타임아웃되어(wrapper 기본
# networkTimeout 10s) 배포가 계속 실패했다. backend 소스가 바뀌지 않는 동안에는
# Docker 레이어 캐시 덕에 증상이 감춰져 있었을 뿐이다.
#
# 이미지 태그는 `gradle/wrapper/gradle-wrapper.properties` 의 distributionUrl 버전과
# 반드시 맞춘다 — 어긋나면 로컬(`./gradlew`)과 CI 의 Gradle 버전이 달라진다.
FROM gradle:9.4.0-jdk21 AS build
WORKDIR /home/gradle/src

# 의존성 레이어를 소스와 분리해 캐시한다 — 소스만 바뀔 때 의존성 재다운로드를 건너뛴다.
# 이 단계는 실패해도 무시한다(캐시 워밍 목적이고, 실제 검증은 아래 bootJar 가 한다).
COPY --chown=gradle:gradle settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY --chown=gradle:gradle . .
RUN gradle --no-daemon bootJar -x test

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
