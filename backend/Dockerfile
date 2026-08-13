# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 소스보다 먼저 복사해서, 소스만 바뀔 때는 의존성 다운로드 레이어를 재사용
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src

# 이미지 빌드 단계에서는 테스트를 생략 (테스트는 CI의 별도 job에서 이미 검증)
RUN ./gradlew war --no-daemon -x test

# ---- Runtime stage ----
FROM tomcat:9.0-jre17-temurin

# LocalDateTime.now()가 컨테이너 기본값(UTC)이 아니라 서비스 기준 시간대로 찍히게 한다.
# JDBC URL의 serverTimezone=Asia/Seoul과 맞춰야 프론트가 그대로 로컬 시각으로 파싱해도 어긋나지 않는다.
ENV TZ=Asia/Seoul

RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/build/libs/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
