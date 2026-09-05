FROM maven:3.9.11-eclipse-temurin-21 AS test
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B test

FROM test AS build
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S jobmatch && adduser -S jobmatch -G jobmatch \
    && mkdir -p /var/lib/jobmatch/cv \
    && chown jobmatch:jobmatch /var/lib/jobmatch/cv
WORKDIR /app
COPY --from=build /workspace/target/jobmatch-*.jar app.jar
USER jobmatch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
