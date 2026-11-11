# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
# Optional secrets support trusted enterprise build proxies; nothing is copied into layers.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings \
    --mount=type=secret,id=java_truststore \
    if [ -f /run/secrets/java_truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/run/secrets/java_truststore"; fi; \
    if [ -f /run/secrets/maven_settings ]; then mvn -s /run/secrets/maven_settings -B -ntp verify; else mvn -B -ntp verify; fi

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -g 10001 -S app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/apiforge-ai-0.1.0.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
