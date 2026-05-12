# 🛠️ Troubleshooting (트러블슈팅)

이 문서는 `security-auth-core` 애플리케이션 개발 및 운영 중 직면할 수 있는 주요 이슈와 그 해결 과정(Debugging & Resolution)을 기록합니다.

## 1. JWT 서명 예외 (SignatureException: HMAC key size mismatch)

### 📌 문제 상황 (Problem)
서버를 기동하고 로그인 API(`/api/auth/login`)를 호출했을 때, 토큰 생성 시점에서 `io.jsonwebtoken.security.WeakKeyException` 또는 `SignatureException`이 발생하며 500 에러가 반환됨.

### 🔍 원인 분석 (Cause Analysis)
- `application.yml`의 `jwt.secret` 키 값이 `HS512` 알고리즘이 요구하는 최소 키 길이(512 bits = 64 bytes)를 충족하지 못했기 때문입니다.
- JWT 최신 라이브러리(jjwt 0.12.x)는 강력한 보안을 위해 규격에 맞지 않는 짧은 비밀키 사용을 런타임에 엄격히 차단합니다.

### 💡 해결 방법 (Resolution)
- `jwt.secret` 값을 최소 64글자 이상의 충분히 길고 무작위적인 문자열(Base64 인코딩)로 변경합니다.
- **해결 스크립트 예시 (리눅스/맥):**
  ```bash
  openssl rand -base64 64
  ```
- 위 명령어로 생성된 문자열을 `application.yml` 또는 환경 변수 `JWT_SECRET`에 적용하여 해결.

---

## 2. Redis 연결 실패 (Connection Refused)

### 📌 문제 상황 (Problem)
`docker-compose up -d`로 실행 후 애플리케이션 로그에 `RedisConnectionFailureException: Unable to connect to Redis` 에러가 무한 반복됨.

### 🔍 원인 분석 (Cause Analysis)
- `security-auth-core` 컨테이너가 `redis` 컨테이너보다 먼저 구동을 완료하고 커넥션을 맺으려 했으나, Redis 프로세스가 아직 시작되지 않아 발생한 타이밍 이슈.
- 혹은 `docker-compose.yml` 내에서 서로 다른 네트워크 대역에 배치되었을 가능성.

### 💡 해결 방법 (Resolution)
1. **의존성 순서 제어:** `docker-compose.yml`에서 애플리케이션 서비스에 `depends_on` 옵션을 부여하여 Redis가 먼저 시작되도록 보장합니다.
2. **Healthcheck 도입:** (고급 해결책) 단순히 시작 여부가 아니라 Redis가 실제 연결을 수락할 준비가 되었는지 `healthcheck`를 통해 검증 후 애플리케이션을 기동하도록 변경. (현재 `depends_on: condition: service_started`로 적용됨)
3. **네트워크 통합:** 두 컨테이너가 `security-network`라는 동일 브릿지 네트워크를 사용하도록 명시적으로 설정하여 통신 가능하게 구성 완료.
