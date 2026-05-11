# ╔══════════════════════════════════════════════════════════════════╗
# ║         🐳 Dockerfile (Java 21 Multi-stage Build)               ║
# ║                                                                  ║
# ║  [빌드 전략]                                                     ║
# ║  1. 빌드 스테이지: JDK 환경에서 소스 컴파일 및 JAR 생성             ║
# ║  2. 실행 스테이지: 가벼운 JRE 환경에서 JAR만 실행 (이미지 경량화)    ║
# ║  3. 보안 최적화: Root 권한이 아닌 별도 유저(spring)로 애플리케이션 실행 ║
# ╚══════════════════════════════════════════════════════════════════╝

# --- 1단계: Build Stage (애플리케이션 빌드) ---
# 가볍고 보안에 강한 alpine 기반 JDK 이미지 사용
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 빌드에 필요한 모든 소스 파일 복사
COPY . .

# Gradle 실행 권한 부여 및 JAR 파일 생성 (테스트 포함 빌드)
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# --- 2단계: Run Stage (최종 이미지 생성) ---
# 실행 전용 JRE 이미지로 교체하여 이미지 크기를 획기적으로 줄임
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 실행 가능한 JAR 파일만 추출하여 가져옴
COPY --from=builder /app/build/libs/*.jar app.jar

# [보안 최적화] Root 권한 실행 차단
# spring이라는 이름의 시스템 그룹과 유저를 생성하여 보안 침해 사고 예방
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 컨테이너 외부로 노출할 포트 설정
EXPOSE 8080

# 애플리케이션 실행 명령어 (JVM 최적화 옵션 등 확장 가능)
ENTRYPOINT ["java", "-jar", "app.jar"]
