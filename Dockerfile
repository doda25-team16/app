FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /frontend

COPY pom.xml .
COPY src/ ./src

RUN mvn clean package

FROM --platform=$TARGETPLATFORM eclipse-temurin:25-alpine 
WORKDIR /frontend

COPY --from=build frontend/target/*.jar frontend.jar

ENV MODEL_HOST="http://localhost:8081"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "frontend.jar"]