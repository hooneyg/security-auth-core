# 🏛️ Security Auth Core Architecture

이 문서는 `security-auth-core` 프로젝트의 핵심 아키텍처 및 세부 도메인별 시스템 흐름을 설명합니다.

---

## 1. System Context Diagram (전체 조감도)
애플리케이션 전체의 구조와 컴포넌트 간의 상호작용을 나타냅니다.

```mermaid
graph TB
    subgraph "🖥️ Client Layer"
        Client["Client (Browser / Mobile)"]
    end

    subgraph "🛡️ Spring Security Filter Chain"
        CorsFilter["CORS Filter"]
        JwtFilter["JWT Authentication Filter"]
        AuthFilter["Authorization Filter"]
    end

    subgraph "🔑 Authentication Core"
        AuthController["Auth Controller"]
        JwtProvider["JWT Token Provider"]
    end

    subgraph "🔐 Encryption Module"
        CryptoService["Hybrid Crypto Service"]
        RSA["RSA-2048 (Key Exchange)"]
        AES["AES-256-GCM (Data Encryption)"]
    end

    subgraph "📦 Token Store (Redis)"
        RefreshRepo["Refresh Token Repository"]
        Blacklist["Token Blacklist"]
    end

    Client --> |"1. Request + Bearer Token"| CorsFilter
    CorsFilter --> JwtFilter
    JwtFilter --> |"2. Validate JWT"| JwtProvider
    JwtFilter --> |"3. Check Blacklist"| Blacklist
    JwtFilter --> AuthFilter
    AuthFilter --> |"4. RBAC Check"| AuthController

    AuthController --> |"5. Issue Tokens"| JwtProvider
    AuthController --> |"6. Store Refresh"| RefreshRepo
    AuthController --> |"7. Encrypt Data"| CryptoService

    CryptoService --> RSA
    CryptoService --> AES

    style Client fill:#1E293B,stroke:#64748B,color:#F8FAFC
    style JwtFilter fill:#7C3AED,stroke:#A855F7,color:#F8FAFC
    style JwtProvider fill:#2563EB,stroke:#3B82F6,color:#F8FAFC
    style CryptoService fill:#DC2626,stroke:#EF4444,color:#F8FAFC
    style RefreshRepo fill:#DC382D,stroke:#EF4444,color:#F8FAFC
    style Blacklist fill:#DC382D,stroke:#EF4444,color:#F8FAFC
```

---

## 2. 세부 도메인 아키텍처

### 2.1 🔄 JWT & Refresh Token Rotation (RTR) Sequence
토큰 탈취(Replay Attack)를 방지하기 위해, Refresh Token 사용 시 기존 토큰을 즉시 폐기하고 새로운 토큰 쌍을 발급하는 회전(Rotation) 로직의 시퀀스입니다.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AuthController
    participant J as JwtTokenProvider
    participant R as Redis (Token Store)

    note over C, R: [초기 로그인 단계]
    C->>A: 1. Login Request (ID/PW)
    A->>J: 2. 토큰 쌍 발급 요청
    J-->>A: 3. Access Token & Refresh Token(A) 발급
    A->>R: 4. Refresh Token(A) 저장 (TTL 7일)
    A-->>C: 5. Tokens 반환

    note over C, R: [토큰 갱신 (RTR) 단계]
    C->>A: 6. Token Refresh Request (with Refresh Token A)
    A->>R: 7. Redis에서 Refresh Token(A) 존재 검증
    R-->>A: 8. Valid (존재함)
    
    A->>J: 9. 새로운 토큰 쌍 발급 요청
    J-->>A: 10. New Access Token & Refresh Token(B) 발급
    
    A->>R: 11. 기존 Refresh Token(A) 삭제 (Rotation 처리)
    A->>R: 12. 새로운 Refresh Token(B) 저장
    A-->>C: 13. New Tokens 반환
```

### 2.2 🔐 Hybrid Encryption Data Flow
민감 데이터 교환 시 RSA의 키 교환 능력과 AES-GCM의 빠른 처리 속도를 결합한 하이브리드 암호화 파이프라인입니다. 매 요청마다 IV를 랜덤하게 생성하여 전방향 안전성(Perfect Forward Secrecy)을 보장합니다.

```mermaid
flowchart TD
    subgraph "🖥️ Client Side"
        C1[평문 데이터 준비] --> C2[일회용 AES 대칭키 생성]
        C2 --> C3[AES 알고리즘으로 평문 암호화]
        C2 --> C4[서버의 RSA 공개키로 AES 키 암호화]
        C3 --> C5[Payload 생성]
        C4 --> C5
    end

    C5 -- "HTTP POST Request" --> S1

    subgraph "서버 환경 (Spring Boot)"
        S1[암호화된 Payload 수신] --> S2[RSA 개인키로 AES 키 복호화]
        S2 -->|복구된 AES 대칭키| S3[AES 키로 실제 데이터 복호화]
        S3 -->|평문 데이터 획득| S4[비즈니스 로직 처리]
    end

    style C2 fill:#F59E0B,color:#fff
    style C4 fill:#DC2626,color:#fff
    style S2 fill:#DC2626,color:#fff
    style S3 fill:#F59E0B,color:#fff
```

### 2.3 🛡️ Spring Security Filter Chain Pipeline
모든 클라이언트 요청이 비즈니스 로직에 도달하기 전 거치는 보안 검문소(Filter)의 내부 처리 파이프라인입니다.

```mermaid
flowchart LR
    Request([Incoming Request]) --> CorsFilter
    
    subgraph "JwtAuthenticationFilter 로직"
        CorsFilter --> CheckHeader{Header에<br>Bearer 토큰<br>존재?}
        CheckHeader -- No --> Pass[다음 필터로 패스]
        CheckHeader -- Yes --> ExtractToken[토큰 추출]
        ExtractToken --> ValidateSignature[HS512 서명 및 만료 검증]
        ValidateSignature --> CheckBlacklist[(Redis Blacklist 조회)]
        CheckBlacklist -- "토큰 존재(로그아웃됨)" --> ThrowError((Throw 401 Unauthorized))
        CheckBlacklist -- "토큰 없음(정상)" --> SetContext[SecurityContextHolder에<br>Authentication 객체 바인딩]
    end
    
    SetContext --> AuthFilter
    Pass --> AuthFilter
    AuthFilter --> |인가 검증 완료| Controller([Controller / API Endpoint])

    style CheckBlacklist fill:#DC382D,color:#fff
    style SetContext fill:#2563EB,color:#fff
```
