# =====================================================================
# Stage 1 - compile the Quarkus application (fast-jar layout)
# Java 21 matches <maven.compiler.release>21 in pom.xml
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Resolve dependencies first so they stay cached between code changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY .mvn .mvn
COPY mvnw mvnw
COPY src ./src
RUN mvn -B clean package -DskipTests

# =====================================================================
# Stage 2 - slim JRE runtime
# =====================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

ENV LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /work

RUN groupadd --system --gid 1001 quarkus \
  && useradd --system --uid 1001 --gid quarkus --home /work quarkus

# Quarkus fast-jar layout: lib/, app/, quarkus/ (build-time generated classes)
# and the quarkus-run.jar launcher. All four are needed at runtime.
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/lib/ /work/lib/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/app/ /work/app/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/quarkus/ /work/quarkus/
COPY --from=build --chown=quarkus:quarkus /build/target/quarkus-app/quarkus-run.jar /work/

USER quarkus
EXPOSE 8080

# /dev/tcp is a bash feature, so the probe must run under bash and not sh.
# The container only reaches this once Spring Boot style readiness is up.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /q/health/ready HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && head -n 1 <&3 | grep -q '200'"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /work/quarkus-run.jar"]
