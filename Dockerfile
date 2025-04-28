FROM gradle:7.5-jdk17 AS builder
WORKDIR /home/gradle/project
COPY . .
RUN gradle clean bootJar -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
