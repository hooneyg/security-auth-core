package com.hooney.lab.security.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║ 📦 Redis 기반 Refresh Token 저장소 ║
 * ║ ║
 * ║ [역할] ║
 * ║ Refresh Token을 Redis에 저장하고 관리합니다. ║
 * ║ 사용자별로 하나의 유효한 Refresh Token만 존재하도록 보장합니다. ║
 * ║ ║
 * ║ [Redis 키 설계] ║
 * ║ 키: "RT:{userId}" → 값: Refresh Token 문자열 ║
 * ║ TTL: Refresh Token 만료 시간과 동일 (7일) ║
 * ║ ║
 * ║ [왜 Redis를 사용하는가?] ║
 * ║ 1. 빠른 읽기/쓰기: 인메모리 DB이므로 평균 1ms 이하의 응답 속도 ║
 * ║ 2. TTL 자동 만료: 토큰 만료 시 자동 삭제 → 별도 배치 작업 불필요 ║
 * ║ 3. 원자적 연산: SET + EXPIRE를 원자적으로 처리 → 경합 조건 방지 ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Redis 키 접두사: Refresh Token을 다른 Redis 데이터와 구분하기 위해 사용
     * 예: "RT:user123" → user123의 Refresh Token
     */
    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    /**
     * 💾 Refresh Token 저장 (또는 갱신)
     *
     * 사용자의 Refresh Token을 Redis에 저장합니다.
     * 기존에 동일 userId의 토큰이 있으면 덮어씁니다. (RTR 시 이전 토큰 무효화)
     *
     * @param userId       사용자 고유 식별자
     * @param refreshToken 저장할 Refresh Token 문자열
     * @param expirationMs 만료 시간 (밀리초) — Redis TTL로 설정됨
     *
     *                     [동작 원리]
     *                     Redis SET 명령 + PEXPIRE 명령을 원자적으로 실행
     *                     → 지정된 시간이 지나면 Redis가 자동으로 해당 키를 삭제합니다.
     */
    public void save(String userId, @NonNull String refreshToken, long expirationMs) {
        String key = REFRESH_TOKEN_PREFIX + userId;

        // opsForValue(): Redis의 String 타입 데이터 조작
        // set(key, value, timeout, unit): 키-값 저장 + TTL 설정을 한 번에 수행
        redisTemplate.opsForValue().set(key, refreshToken, expirationMs, TimeUnit.MILLISECONDS);

        log.info("💾 Refresh Token 저장 — userId: {}, TTL: {}ms", userId, expirationMs);
    }

    /**
     * 🔍 Refresh Token 조회
     *
     * 사용자의 현재 유효한 Refresh Token을 Redis에서 조회합니다.
     *
     * @param userId 사용자 고유 식별자
     * @return 저장된 Refresh Token (만료되었거나 없으면 null)
     */
    public String findByUserId(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 🗑️ Refresh Token 삭제 (로그아웃 시)
     *
     * 사용자의 Refresh Token을 Redis에서 삭제합니다.
     * 로그아웃하거나, RTR로 새 토큰을 발급한 뒤 이전 토큰을 무효화할 때 호출됩니다.
     *
     * @param userId 사용자 고유 식별자
     */
    public void deleteByUserId(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("🗑️ Refresh Token 삭제 완료 — userId: {}", userId);
        } else {
            log.warn("⚠️ 삭제할 Refresh Token이 존재하지 않음 — userId: {}", userId);
        }
    }

    /**
     * ✅ 저장된 Refresh Token과 요청 토큰 일치 여부 확인
     *
     * RTR(Refresh Token Rotation)에서 핵심적인 역할을 합니다:
     * - 일치하면: 정당한 토큰 갱신 요청 → 새 토큰 발급
     * - 불일치하면: 탈취된 이전 토큰 사용 시도 → 모든 토큰 무효화 필요
     *
     * @param userId       사용자 고유 식별자
     * @param refreshToken 검증할 Refresh Token
     * @return 저장된 토큰과 일치하면 true
     */
    public boolean validateRefreshToken(String userId, String refreshToken) {
        String storedToken = findByUserId(userId);

        if (storedToken == null) {
            log.warn("🚨 Refresh Token이 Redis에 존재하지 않음 — userId: {} (만료 또는 로그아웃)", userId);
            return false;
        }

        boolean isValid = storedToken.equals(refreshToken);

        if (!isValid) {
            // 저장된 토큰과 다른 토큰이 사용됨
            // → RTR 위반: 이미 교체된 이전 토큰이 재사용되었을 가능성
            // → 보안 대응: 해당 사용자의 모든 토큰을 무효화하는 것을 권장
            log.error("🚨 Refresh Token 불일치 감지 — userId: {} (토큰 탈취 의심!)", userId);
        }

        return isValid;
    }
}
