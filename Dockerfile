FROM maven:3.9.11-eclipse-temurin-25
WORKDIR /frontend

COPY pom.xml .
COPY src/ ./src

RUN MODEL_HOST="http://localhost:8081" mvn clean package

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/frontend-0.0.1-SNAPSHOT.jar"]