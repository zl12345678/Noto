FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S noto && adduser -S noto -G noto
USER noto
COPY --from=build /app/target/*.jar app.jar
EXPOSE 9086
ENTRYPOINT ["java", "-jar", "app.jar"]
