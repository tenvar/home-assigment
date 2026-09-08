FROM gradle:9.7.1-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S wallet && adduser -S wallet -G wallet
COPY --from=build /app/build/libs/wallet.jar app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
