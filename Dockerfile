FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /frontend

COPY pom.xml .
COPY src/ ./src
COPY maven-settings.xml /tmp/settings.xml

# BuildKit secret: provide token only during this RUN step
RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    mvn -s /tmp/settings.xml -B clean package

FROM eclipse-temurin:25-alpine
WORKDIR /frontend

COPY --from=build /frontend/target/*.jar frontend.jar

ENV MODEL_HOST="http://sms-model:8081"
ENV SERVER_PORT="8080"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "frontend.jar"]
