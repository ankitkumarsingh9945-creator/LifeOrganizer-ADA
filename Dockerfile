FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
COPY database database
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/life-organizer-ada-2.0.0.jar app.jar
ENV PORT=80 SERVER_ADDRESS=0.0.0.0 SESSION_COOKIE_SECURE=true SPRING_PROFILES_ACTIVE=neon
EXPOSE 80
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
