# RECARRY backend — 어느 컨테이너 호스팅에서도 쓸 수 있는 이미지 (provider 중립)
#   docker build -t recarry-backend backend/
#   docker run -p 8080:8080 --env-file <운영 env 파일> recarry-backend
# 비밀 값은 이미지에 넣지 않는다 (.dockerignore 가 .env* 를 막는다) — 실행 시 환경변수로만 준다.
# 테스트는 여기서 돌리지 않는다 (PostgreSQL 이 필요 — CI 가 돌린다).

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows 에서 체크아웃한 경우를 대비해 줄바꿈을 LF 로
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/recarry-backend.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# 호스팅이 PORT 를 주면 그 포트로 뜬다 (server.port: ${PORT:8080}, Render 는 10000)
# heap 은 컨테이너 메모리의 60% — 512MB 인스턴스에서 heap ~300MB + 비힙(metaspace·스레드 등) 여유를 남긴다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
