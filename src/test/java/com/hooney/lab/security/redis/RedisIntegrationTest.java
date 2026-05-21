package com.hooney.lab.security.redis;

import com.hooney.lab.config.EmbeddedRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║             📦 Redis 통합 테스트 (Token Store 검증)               ║
 * ║                                                                  ║
 * ║  [테스트 목적]                                                    ║
 * ║  Embedded Redis 환경을 띄우고, Spring Data Redis 인터페이스가     ║
 * ║  실제 캐시 서버와 원활히 통신하는지 검증합니다.                   ║
 * ║                                                                  ║
 * ║  [주요 검증 항목]                                                 ║
 * ║  1. Refresh Token 저장 및 정확한 데이터 조회 (RTR 기반)           ║
 * ║  2. Blacklist 등록 및 검증을 통한 로그아웃 토큰 사용 차단         ║
 * ║  3. 사용이 완료된 토큰의 정상적인 Redis 키 삭제 로직              ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@SpringBootTest
(properties = {
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=",       // 패스워드 공백으로 완벽 차단
    "spring.data.redis.timeout=5000ms"   // 핸드셰이크 타임아웃을 위해 여유 있게 지정
})
@Import(EmbeddedRedisConfig.class)
class RedisIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RedisTokenBlacklist redisTokenBlacklist;

    @Test
    @DisplayName("Refresh Token 기능 검증: 토큰을 Redis에 저장하고, 다시 조회할 수 있어야 한다")
    void saveAndFindRefreshToken() {
        // given
        // 사용자 ID와 발급된 임의의 Refresh Token, 그리고 만료 시간(5분)을 설정합니다.
        String userId = "test_user@hooneyz.com";
        String refreshToken = "eyJhbGciOiJIUzUxMiJ9.test.refresh.token";
        long expirationMs = 300_000L; // 5분 = 300,000 밀리초

        // when
        // Redis 저장소에 토큰을 저장합니다. (opsForValue().set 사용)
        refreshTokenRepository.save(userId, refreshToken, expirationMs);

        // then
        // 저장한 사용자 ID로 토큰을 조회했을 때, 저장한 값과 정확히 일치해야 합니다.
        String savedToken = refreshTokenRepository.findByUserId(userId);
        assertThat(savedToken).isEqualTo(refreshToken);
    }

    @Test
    @DisplayName("Blacklist 기능 검증: 로그아웃 시 Access Token을 Blacklist에 등록하면, 차단 상태(true)로 조회되어야 한다")
    void addToBlacklistAndCheck() {
        // given
        // 만료되지 않았지만 로그아웃 처리된 임의의 Access Token을 준비합니다.
        String accessToken = "eyJhbGciOiJIUzUxMiJ9.test.access.token";
        long remainingTtlMs = 600_000L; // 남은 수명 10분 = 600,000 밀리초

        // when
        // Redis 블랙리스트에 해당 토큰을 'blacklisted' 마커와 함께 등록합니다.
        redisTokenBlacklist.addToBlacklist(accessToken, remainingTtlMs);

        // then
        // 블랙리스트에 등록된 토큰을 isBlacklisted 메서드로 조회하면 true를 반환해야 합니다.
        boolean isBlacklisted = redisTokenBlacklist.isBlacklisted(accessToken);
        assertThat(isBlacklisted).isTrue();
    }

    @Test
    @DisplayName("RTR 무효화 검증: Redis에 저장된 Refresh Token을 명시적으로 삭제하면, 더 이상 조회되지 않아야 한다")
    void deleteRefreshToken() {
        // given
        // 특정 사용자의 Refresh Token을 미리 저장해 둡니다.
        String userId = "delete_user@hooneyz.com";
        String refreshToken = "eyJhbGciOiJIUzUxMiJ9.delete.token";
        refreshTokenRepository.save(userId, refreshToken, 300_000L);

        // when
        // RTR(Refresh Token Rotation) 원칙에 따라, 갱신이 완료된 이전 토큰을 삭제합니다.
        refreshTokenRepository.deleteByUserId(userId);

        // then
        // 삭제 후에는 해당 사용자의 토큰이 Redis에서 조회되지 않아야 합니다 (null 반환).
        String savedToken = refreshTokenRepository.findByUserId(userId);
        assertThat(savedToken).isNull();
    }
}
