# 빌드 단계와 실행 단계를 나눈다. 실행 이미지에 그래들과 소스가 남지 않도록.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# 의존성만 먼저 받아 두면 소스만 바뀐 빌드에서 이 층을 다시 쓰게 된다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN sh gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src ./src
RUN sh gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre

WORKDIR /srv
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
