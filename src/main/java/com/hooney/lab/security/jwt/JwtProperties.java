package com.hooney.lab.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🔑 JWT 설정 프로퍼티 바인딩 클래스                        ║
 * ║                                                                  ║
 * ║  [역할]                                                          ║
 * ║  application.yml의 'jwt' 프리픽스 아래 설정값들을 Java 객체로      ║
 * ║  자동 바인딩합니다. 이렇게 하면 설정값을 타입 세이프하게 사용 가능.  ║
 * ║                                                                  ║
 * ║  [바인딩 매핑]                                                    ║
 * ║  jwt.secret                    → this.secret                    ║
 * ║  jwt.access-token.expiration   → this.accessToken.expiration    ║
 * ║  jwt.refresh-token.expiration  → this.refreshToken.expiration   ║
 * ║  jwt.rtr.enabled               → this.rtr.enabled              ║
 * ║                                                                  ║
 * ║  [사용법]                                                        ║
 * ║  @Autowired JwtProperties props; → props.getSecret()            ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 서명에 사용되는 비밀키
     * HMAC-SHA512 알고리즘 기준 최소 64자(512비트) 이상을 권장합니다.
     *
     * [생성 방법]
     * Linux/Mac: openssl rand -base64 64
     * Windows:   [Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Max 256 }))
     */
    private String secret;

    /**
     * Access Token 관련 설정을 그룹화한 내부 클래스
     */
    private AccessToken accessToken = new AccessToken();

    /**
     * Refresh Token 관련 설정을 그룹화한 내부 클래스
     */
    private RefreshToken refreshToken = new RefreshToken();

    /**
     * RTR(Refresh Token Rotation) 관련 설정
     */
    private Rtr rtr = new Rtr();

    @Getter
    @Setter
    public static class AccessToken {
        /**
         * Access Token 만료 시간 (밀리초)
         * 기본값: 900000ms = 15분
         *
         * [보안 vs 편의성 트레이드오프]
         * - 5분 이하: 매우 높은 보안, 하지만 사용자 경험 저하
         * - 15분: 업계 표준 권장치 (금융 시스템 기준)
         * - 1시간: 일반 웹 서비스 적합 수준
         * - 1시간 초과: 보안 위험 증가 (비권장)
         */
        private long expiration = 900000;
    }

    @Getter
    @Setter
    public static class RefreshToken {
        /**
         * Refresh Token 만료 시간 (밀리초)
         * 기본값: 604800000ms = 7일
         *
         * [설계 고려사항]
         * - 너무 짧으면: 사용자가 자주 재로그인해야 함
         * - 너무 길면: 탈취된 토큰이 오래 유효하여 보안 위험
         * - 권장: 7~14일 (RTR 활성화 시 보안 보완)
         */
        private long expiration = 604800000;
    }

    @Getter
    @Setter
    public static class Rtr {
        /**
         * RTR(Refresh Token Rotation) 활성화 여부
         *
         * [RTR이란?]
         * Refresh Token을 사용할 때마다 새로운 Refresh Token을 발급하고
         * 이전 토큰을 즉시 무효화하는 전략입니다.
         *
         * [보안 효과]
         * 공격자가 Refresh Token을 탈취하더라도:
         * 1. 정당한 사용자가 먼저 사용하면 → 공격자의 토큰은 이미 무효
         * 2. 공격자가 먼저 사용하면 → 정당한 사용자의 갱신 실패 → 이상 탐지 가능
         *
         * [권장] 항상 true로 설정
         */
        private boolean enabled = true;
    }
}
