# 🧪 security-auth-core build test
docker run --rm -v ${PWD}:/app -w /app eclipse-temurin:21-jdk ./gradlew test --no-daemon
