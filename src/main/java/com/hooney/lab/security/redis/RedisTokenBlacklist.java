package com.hooney.lab.security.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🚫 Redis 기반 JWT 토큰 블랙리스트                        ║
 * ║                                                                  ║
 * ║  [이 클래스의 역할]                                               ║
 * ║  로그아웃된 Access Token을 블랙리스트에 등록하여                   ║
 * ║  해당 토큰이 만료 전에 재사용되는 것을 방지합니다.                 ║
 * ║                                                                  ║
 * ║  [왜 블랙리스트가 필요한가?]                                      ║
 * ║  JWT는 Stateless 토큰이므로 서버가 일방적으로 무효화할 수 없습니다.║
 * ║  토큰 자체에 만료 시간이 포함되어 있어, 유효 기간 내에는 계속      ║
 * ║  사용 가능합니다. 따라서 로그아웃 시 블랙리스트에 등록하여          ║
 * ║  해당 토큰의 사용을 차단해야 합니다.                               ║
 * ║                                                                  ║
 * ║  [Redis 키 설계]                                                  ║
 * ║  키: "BL:{tokenHash}" → 값: "blacklisted"                       ║
 * ║  TTL: 해당 토큰의 남은 유효 시간과 동일                           ║
 * ║       (토큰이 어차피 만료되면 블랙리스트에서도 자동 삭제)          ║
 * ║                                                                  ║
 * ║  [성능 고려사항]                                                   ║
 * ║  - 블랙리스트 조회는 모든 API 요청마다 수행되므로 O(1) 성능 필수  ║
 * ║  - Redis의 GET 연산은 평균 0.1ms 이하 → 성능 영향 미미            ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklist {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 블랙리스트 키 접두사
     * "BL:" 접두사를 사용하여 다른 Redis 데이터와 네임스페이스 분리
     */
    private static final String BLACKLIST_PREFIX = "BL:";

    /** 블랙리스트에 등록된 토큰의 값 (단순 마커 역할) */
    private static final String BLACKLISTED_VALUE = "blacklisted";

    /**
     * 🚫 Access Token을 블랙리스트에 등록
     *
     * 로그아웃 시 현재 사용 중인 Access Token을 블랙리스트에 추가합니다.
     * TTL을 토큰의 남은 유효 시간으로 설정하여, 토큰 만료 후 자동 삭제합니다.
     *
     * @param token        블랙리스트에 등록할 Access Token 문자열
     * @param expirationMs 토큰의 남은 유효 시간 (밀리초)
     *                     → Redis TTL로 사용되어 자동 정리됨
     *
     * [예시]
     * Access Token이 15분(900000ms) 후 만료 예정이고,
     * 사용한 지 10분이 지났다면:
     * - 남은 시간 = 5분(300000ms)
     * - Redis TTL = 300000ms
     * - 5분 후 Redis에서 자동 삭제됨
     */
    public void addToBlacklist(String token, long expirationMs) {
        // 남은 유효 시간이 0 이하면 이미 만료된 토큰이므로 등록 불필요
        if (expirationMs <= 0) {
            log.debug("⏭️ 이미 만료된 토큰 — 블랙리스트 등록 생략");
            return;
        }

        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, BLACKLISTED_VALUE, expirationMs, TimeUnit.MILLISECONDS);

        log.info("🚫 토큰 블랙리스트 등록 완료 (TTL: {}ms)", expirationMs);
    }

    /**
     * ✅ 토큰이 블랙리스트에 등록되어 있는지 확인
     *
     * JWT 인증 필터에서 모든 API 요청마다 호출됩니다.
     * 블랙리스트에 있으면 해당 토큰은 로그아웃된 것이므로 인증 거부합니다.
     *
     * @param token 확인할 Access Token
     * @return 블랙리스트에 있으면 true (= 로그아웃된 토큰), 없으면 false
     *
     * [성능]
     * Redis GET 연산: O(1), 평균 0.1ms 이하
     * 모든 요청마다 호출해도 성능 영향 미미
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            log.debug("🚫 블랙리스트 토큰 감지 — 접근 차단");
        }

        return Boolean.TRUE.equals(exists);
    }
}
