package com.hooney.lab.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🔑 JWT 토큰 생성 / 검증 / 파싱 핵심 프로바이더            ║
 * ║                                                                  ║
 * ║  [이 클래스의 책임]                                               ║
 * ║  1. Access Token 생성: 사용자 인증 후 15분 유효 토큰 발급          ║
 * ║  2. Refresh Token 생성: Access Token 갱신용 7일 유효 토큰 발급    ║
 * ║  3. 토큰 검증: 서명, 만료, 구조 등 유효성 검사                     ║
 * ║  4. 토큰 파싱: 토큰에서 사용자 정보(Claims) 추출                   ║
 * ║  5. Authentication 객체 생성: Spring Security 인증 컨텍스트 연동   ║
 * ║                                                                  ║
 * ║  [JWT 토큰 구조] (점(.)으로 3파트 구분)                            ║
 * ║  Header.Payload.Signature                                        ║
 * ║  ─ Header: 알고리즘(HS512), 토큰 타입(JWT) 정보                   ║
 * ║  ─ Payload: 사용자 ID, 역할, 만료 시간 등 클레임(Claim) 데이터     ║
 * ║  ─ Signature: Header + Payload를 비밀키로 서명한 해시값            ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * HMAC-SHA512 서명에 사용되는 비밀키 객체.
     * 문자열 형태의 시크릿을 Base64 디코딩하여 SecretKey로 변환합니다.
     * 이 키는 토큰 서명(생성)과 검증 모두에 사용됩니다.
     */
    private SecretKey signingKey;

    /**
     * 🔧 초기화 메서드
     *
     * Spring Bean 생성 후 자동 호출되며, application.yml의 jwt.secret 값을
     * HMAC-SHA 알고리즘에 적합한 SecretKey 객체로 변환합니다.
     *
     * [왜 @PostConstruct를 사용하는가?]
     * - 생성자에서 하면 JwtProperties가 아직 주입되지 않았을 수 있음
     * - @PostConstruct는 의존성 주입이 완료된 후 호출되므로 안전
     */
    @PostConstruct
    protected void init() {
        // Base64로 인코딩된 시크릿 문자열을 바이트 배열로 디코딩
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());

        // 바이트 배열로부터 HMAC-SHA 서명용 SecretKey 생성
        // Keys.hmacShaKeyFor()는 키 길이에 따라 자동으로 HS256/HS384/HS512를 선택
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        log.info("✅ JWT 서명 키 초기화 완료 (키 길이: {} bytes)", keyBytes.length);
    }

    /**
     * 🎫 Access Token 생성
     *
     * 사용자 인증 성공 시 호출되어 단기 유효 토큰을 발급합니다.
     * 이 토큰은 모든 API 요청의 Authorization 헤더에 포함되어 사용됩니다.
     *
     * @param userId 사용자 고유 식별자 (예: "user123" 또는 UUID)
     * @param roles  사용자 역할 목록 (예: ["ROLE_USER", "ROLE_ADMIN"])
     * @return JWT 문자열 (예: "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOi...")
     *
     * [토큰 페이로드(Payload) 구성]
     * {
     *   "sub": "user123",              ← Subject: 토큰 소유자 식별자
     *   "roles": "ROLE_USER,ROLE_ADMIN", ← 역할 목록 (콤마 구분 문자열)
     *   "type": "ACCESS",              ← 토큰 유형 (Access vs Refresh 구분용)
     *   "iat": 1715450000,             ← Issued At: 발급 시각 (Unix timestamp)
     *   "exp": 1715450900              ← Expiration: 만료 시각
     * }
     */
    public String createAccessToken(String userId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessToken().getExpiration());

        return Jwts.builder()
                // Subject: 토큰의 주체(소유자)를 식별하는 표준 클레임
                .subject(userId)

                // Custom Claim: 사용자 역할 정보 (쉼표로 구분된 문자열)
                // 예: "ROLE_USER,ROLE_ADMIN"
                .claim("roles", String.join(",", roles))

                // Custom Claim: 토큰 유형 (Access / Refresh 구분)
                .claim("type", "ACCESS")

                // Issued At: 토큰 발급 시각
                .issuedAt(now)

                // Expiration: 토큰 만료 시각 (이 시각 이후에는 토큰 무효)
                .expiration(expiry)

                // Signature: 비밀키로 서명하여 토큰 위변조 방지
                .signWith(signingKey)

                // 최종 JWT 문자열 생성 (Header.Payload.Signature)
                .compact();
    }

    /**
     * 🔄 Refresh Token 생성
     *
     * Access Token이 만료되었을 때 새로운 Access Token을 발급받기 위한
     * 장기 유효 토큰입니다. Redis에 저장하여 관리합니다.
     *
     * @param userId 사용자 고유 식별자
     * @return Refresh Token JWT 문자열
     *
     * [Access Token과의 차이점]
     * - 만료 시간이 훨씬 김 (7일 vs 15분)
     * - 역할(roles) 정보를 포함하지 않음 (토큰 갱신 시 DB에서 최신 역할 조회)
     * - Redis에 별도 저장되어 서버 측에서 무효화(revoke) 가능
     */
    public String createRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshToken().getExpiration());

        return Jwts.builder()
                .subject(userId)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * ✅ 토큰 유효성 검증
     *
     * 다음 항목들을 순서대로 검증합니다:
     * 1. 서명 검증: 비밀키로 서명이 올바른지 확인 (위변조 탐지)
     * 2. 만료 검증: 현재 시각이 exp 클레임 이전인지 확인
     * 3. 구조 검증: JWT가 올바른 3파트 구조(Header.Payload.Signature)인지 확인
     *
     * @param token 검증할 JWT 문자열
     * @return 유효하면 true, 그 외 false
     */
    public boolean validateToken(String token) {
        try {
            // JWT 파서를 생성하고 서명 검증용 키를 설정한 뒤 토큰을 파싱
            // 파싱 과정에서 서명, 만료, 구조 등이 모두 자동 검증됨
            Jwts.parser()
                    .verifyWith(signingKey)  // 서명 검증에 사용할 키 설정
                    .build()
                    .parseSignedClaims(token);  // 파싱 + 검증 수행

            return true;

        } catch (SecurityException | MalformedJwtException e) {
            // 서명이 올바르지 않거나 JWT 구조가 잘못된 경우
            // → 토큰이 위변조되었을 가능성 높음
            log.error("🚨 유효하지 않은 JWT 서명입니다: {}", e.getMessage());

        } catch (ExpiredJwtException e) {
            // 토큰의 exp 클레임이 현재 시각보다 이전인 경우
            // → 정상적인 만료이므로 Refresh Token으로 갱신 필요
            log.warn("⏰ 만료된 JWT 토큰입니다: {}", e.getMessage());

        } catch (UnsupportedJwtException e) {
            // 지원하지 않는 JWT 형식 (예: JWE 등)
            log.error("❌ 지원되지 않는 JWT 토큰입니다: {}", e.getMessage());

        } catch (IllegalArgumentException e) {
            // 토큰이 null이거나 빈 문자열인 경우
            log.error("❌ JWT 토큰이 비어있습니다: {}", e.getMessage());
        }

        return false;
    }

    /**
     * 👤 JWT에서 Spring Security Authentication 객체 생성
     *
     * 토큰 검증 후 SecurityContextHolder에 저장할 Authentication 객체를 만듭니다.
     * 이 객체가 있어야 Spring Security가 해당 요청을 "인증된 요청"으로 인식합니다.
     *
     * @param token 유효성이 검증된 JWT 문자열
     * @return Spring Security Authentication 객체
     *
     * [Authentication 객체 구성]
     * - Principal: UserDetails 객체 (사용자 정보 + 권한)
     * - Credentials: 비밀번호 (JWT 인증에서는 빈 문자열)
     * - Authorities: 사용자 권한 목록 (ROLE_USER, ROLE_ADMIN 등)
     */
    public Authentication getAuthentication(String token) {
        // 토큰에서 클레임(Payload) 추출
        Claims claims = parseClaims(token);

        // roles 클레임에서 권한 목록 추출
        // "ROLE_USER,ROLE_ADMIN" → [SimpleGrantedAuthority("ROLE_USER"), ...]
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("roles", String.class).split(","))
                        .filter(role -> !role.isBlank())  // 빈 문자열 필터링
                        .map(SimpleGrantedAuthority::new)  // 문자열 → Authority 변환
                        .collect(Collectors.toList());

        // Spring Security의 UserDetails 구현체 생성
        // username: JWT의 subject (사용자 ID)
        // password: "" (JWT 인증에서는 비밀번호 불필요)
        // authorities: 위에서 추출한 권한 목록
        User principal = new User(claims.getSubject(), "", authorities);

        // UsernamePasswordAuthenticationToken: Spring Security의 표준 Authentication 구현체
        // 3개 파라미터 생성자를 사용하면 자동으로 authenticated=true로 설정됨
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    /**
     * 📋 토큰에서 사용자 ID(Subject) 추출
     *
     * @param token JWT 문자열
     * @return 사용자 ID (예: "user123")
     */
    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 📋 토큰에서 토큰 유형(type) 추출
     *
     * @param token JWT 문자열
     * @return 토큰 유형 ("ACCESS" 또는 "REFRESH")
     */
    public String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    /**
     * ⏱️ 토큰의 남은 유효 시간 계산 (밀리초)
     *
     * 토큰 블랙리스트 등록 시 Redis의 TTL(Time To Live)을 설정하기 위해 사용합니다.
     * 만료된 토큰은 블랙리스트에 등록할 필요가 없으므로 음수가 되면 0을 반환합니다.
     *
     * @param token JWT 문자열
     * @return 남은 유효 시간 (밀리초), 이미 만료된 경우 0
     */
    public long getRemainingExpiration(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);  // 음수 방지
        } catch (ExpiredJwtException e) {
            return 0;  // 이미 만료된 토큰
        }
    }

    /**
     * 🔍 JWT 클레임(Claims) 파싱 내부 메서드
     *
     * JWT 문자열을 파싱하여 Payload 부분의 Claims 객체를 추출합니다.
     * Claims는 Map<String, Object>와 유사한 구조로,
     * subject, expiration 등의 표준 클레임과 커스텀 클레임을 포함합니다.
     *
     * @param token JWT 문자열
     * @return Claims 객체 (토큰의 Payload 데이터)
     * @throws ExpiredJwtException 토큰이 만료된 경우
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 만료된 토큰이더라도 Claims 데이터는 추출 가능
            // (로그아웃 시 블랙리스트 등록에 필요)
            return e.getClaims();
        }
    }
}
