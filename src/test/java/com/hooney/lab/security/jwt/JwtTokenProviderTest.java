package com.hooney.lab.security.jwt;

import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🧪 JwtTokenProvider 단위 테스트                          ║
 * ║                                                                  ║
 * ║  [테스트 범위]                                                    ║
 * ║  1. Access Token 생성 및 유효성 검증                              ║
 * ║  2. Refresh Token 생성 및 유효성 검증                             ║
 * ║  3. 토큰에서 사용자 정보(Claims) 추출                             ║
 * ║  4. 만료된 토큰 검증 실패 확인                                    ║
 * ║  5. 위변조된 토큰 검증 실패 확인                                  ║
 * ║  6. 토큰 남은 유효 시간 계산                                      ║
 * ║  7. Authentication 객체 생성 정합성                               ║
 * ║                                                                  ║
 * ║  [테스트 전략]                                                    ║
 * ║  - Spring Context 없이 순수 단위 테스트 수행                      ║
 * ║  - 테스트용 시크릿 키를 생성하여 독립적으로 실행                    ║
 * ║  - @TestMethodOrder: 논리적 순서대로 테스트 실행                   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("🔑 JWT Token Provider 테스트")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    // 테스트에서 사용할 상수
    private static final String TEST_USER_ID = "testUser123";
    private static final List<String> TEST_ROLES = Arrays.asList("ROLE_USER", "ROLE_ADMIN");

    /**
     * 각 테스트 실행 전 JwtTokenProvider를 수동 초기화합니다.
     * Spring Context를 사용하지 않으므로 직접 의존성을 주입합니다.
     */
    @BeforeEach
    void setUp() throws Exception {
        // ── Step 1: JwtProperties 수동 생성 및 설정 ──
        jwtProperties = new JwtProperties();

        // 테스트용 HMAC-SHA512 시크릿 키 생성 (512비트 = 64바이트)
        // 실제 운영에서는 application.yml에서 읽어오지만, 테스트에서는 직접 생성
        SecretKey testKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS512);
        String base64Secret = Encoders.BASE64.encode(testKey.getEncoded());
        jwtProperties.setSecret(base64Secret);

        // Access Token 만료: 15분 (테스트 중에는 만료되지 않음)
        JwtProperties.AccessToken accessToken = new JwtProperties.AccessToken();
        accessToken.setExpiration(900000L);
        jwtProperties.setAccessToken(accessToken);

        // Refresh Token 만료: 7일
        JwtProperties.RefreshToken refreshToken = new JwtProperties.RefreshToken();
        refreshToken.setExpiration(604800000L);
        jwtProperties.setRefreshToken(refreshToken);

        // RTR 활성화
        JwtProperties.Rtr rtr = new JwtProperties.Rtr();
        rtr.setEnabled(true);
        jwtProperties.setRtr(rtr);

        // ── Step 2: JwtTokenProvider 생성 + @PostConstruct 수동 호출 ──
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();  // @PostConstruct 메서드 수동 호출
    }

    @Test
    @Order(1)
    @DisplayName("✅ Access Token 생성 — 정상적으로 JWT 문자열이 생성되어야 한다")
    void createAccessToken_ShouldReturnValidJwtString() {
        // When: Access Token 생성
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // Then: 토큰이 null이 아니고, JWT 표준 3파트 구조(xxx.yyy.zzz)를 가져야 함
        assertThat(token)
                .isNotNull()
                .isNotBlank();

        // JWT는 점(.)으로 구분된 3개 파트로 구성됨: Header.Payload.Signature
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        System.out.println("✅ 생성된 Access Token: " + token.substring(0, 50) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("✅ Refresh Token 생성 — 정상적으로 JWT 문자열이 생성되어야 한다")
    void createRefreshToken_ShouldReturnValidJwtString() {
        // When: Refresh Token 생성
        String token = jwtTokenProvider.createRefreshToken(TEST_USER_ID);

        // Then: 유효한 JWT 구조
        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        System.out.println("✅ 생성된 Refresh Token: " + token.substring(0, 50) + "...");
    }

    @Test
    @Order(3)
    @DisplayName("✅ 토큰 유효성 검증 — 방금 생성한 토큰은 유효해야 한다")
    void validateToken_WithValidToken_ShouldReturnTrue() {
        // Given: 유효한 Access Token 생성
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // When & Then: 검증 결과 true
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();

        System.out.println("✅ 토큰 유효성 검증 통과");
    }

    @Test
    @Order(4)
    @DisplayName("❌ 위변조 토큰 검증 — 서명이 변경된 토큰은 거부되어야 한다")
    void validateToken_WithTamperedToken_ShouldReturnFalse() {
        // Given: 유효한 토큰 생성
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // When: 토큰 마지막 글자를 변경하여 서명 위변조
        String tamperedToken = token.substring(0, token.length() - 1) + "X";

        // Then: 위변조된 토큰은 검증 실패
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();

        System.out.println("✅ 위변조 토큰 검증 거부 확인");
    }

    @Test
    @Order(5)
    @DisplayName("❌ 만료된 토큰 검증 — 유효 기간이 지난 토큰은 거부되어야 한다")
    void validateToken_WithExpiredToken_ShouldReturnFalse() throws Exception {
        // Given: 만료 시간을 1ms로 설정하여 즉시 만료되는 토큰 생성
        jwtProperties.getAccessToken().setExpiration(1L);

        // JwtTokenProvider 재초기화
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(jwtProperties);
        shortLivedProvider.init();

        String token = shortLivedProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // 토큰이 확실히 만료되도록 잠시 대기
        Thread.sleep(50);

        // Then: 만료된 토큰은 검증 실패
        assertThat(shortLivedProvider.validateToken(token)).isFalse();

        System.out.println("✅ 만료 토큰 검증 거부 확인");
    }

    @Test
    @Order(6)
    @DisplayName("✅ 사용자 ID 추출 — 토큰에서 Subject(사용자 ID)를 정확히 추출해야 한다")
    void getUserId_ShouldReturnCorrectUserId() {
        // Given
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // When
        String userId = jwtTokenProvider.getUserId(token);

        // Then: 토큰 생성 시 넣은 userId와 동일해야 함
        assertThat(userId).isEqualTo(TEST_USER_ID);

        System.out.println("✅ 추출된 사용자 ID: " + userId);
    }

    @Test
    @Order(7)
    @DisplayName("✅ 토큰 타입 추출 — Access Token은 'ACCESS', Refresh Token은 'REFRESH'여야 한다")
    void getTokenType_ShouldReturnCorrectType() {
        // Given: 두 종류의 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);
        String refreshToken = jwtTokenProvider.createRefreshToken(TEST_USER_ID);

        // Then: 각 토큰의 type 클레임이 정확해야 함
        assertThat(jwtTokenProvider.getTokenType(accessToken)).isEqualTo("ACCESS");
        assertThat(jwtTokenProvider.getTokenType(refreshToken)).isEqualTo("REFRESH");

        System.out.println("✅ Access Token type: ACCESS, Refresh Token type: REFRESH");
    }

    @Test
    @Order(8)
    @DisplayName("✅ Authentication 객체 생성 — 토큰에서 올바른 권한 정보가 추출되어야 한다")
    void getAuthentication_ShouldReturnValidAuthentication() {
        // Given: 역할 정보를 포함한 Access Token 생성
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // When: Authentication 객체 생성
        var authentication = jwtTokenProvider.getAuthentication(token);

        // Then: Principal의 username이 userId와 일치
        assertThat(authentication.getName()).isEqualTo(TEST_USER_ID);

        // Then: 권한 목록에 ROLE_USER와 ROLE_ADMIN이 포함
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");

        // Then: 인증 상태가 true
        assertThat(authentication.isAuthenticated()).isTrue();

        System.out.println("✅ Authentication 생성 성공 — 사용자: " + authentication.getName()
                + ", 권한: " + authentication.getAuthorities());
    }

    @Test
    @Order(9)
    @DisplayName("✅ 남은 유효 시간 계산 — 양수 값을 반환해야 한다")
    void getRemainingExpiration_ShouldReturnPositiveValue() {
        // Given: 15분 만료 Access Token 생성
        String token = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_ROLES);

        // When: 남은 유효 시간 계산
        long remaining = jwtTokenProvider.getRemainingExpiration(token);

        // Then: 0보다 크고, 만료 시간(15분) 이하여야 함
        assertThat(remaining).isGreaterThan(0);
        assertThat(remaining).isLessThanOrEqualTo(jwtProperties.getAccessToken().getExpiration());

        System.out.println("✅ 남은 유효 시간: " + remaining + "ms (~" + (remaining / 1000) + "초)");
    }

    @Test
    @Order(10)
    @DisplayName("❌ 빈 토큰 검증 — null 또는 빈 문자열은 거부되어야 한다")
    void validateToken_WithNullOrEmpty_ShouldReturnFalse() {
        // null 토큰
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();

        // 빈 문자열 토큰
        assertThat(jwtTokenProvider.validateToken("")).isFalse();

        // 완전히 잘못된 형식의 토큰
        assertThat(jwtTokenProvider.validateToken("not.a.valid.jwt")).isFalse();

        System.out.println("✅ 잘못된 토큰 형식 거부 확인");
    }
}
