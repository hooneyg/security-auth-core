package com.hooney.lab.config;

import com.hooney.lab.security.jwt.JwtAuthenticationFilter;
import com.hooney.lab.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🛡️ Spring Security 6.x 핵심 보안 설정 클래스             ║
 * ║                                                                  ║
 * ║  [이 클래스의 역할]                                               ║
 * ║  Spring Security의 HTTP 보안 필터 체인(Filter Chain)을 구성합니다. ║
 * ║  모든 HTTP 요청은 이 필터 체인을 통과하며, 인증/인가가 처리됩니다.  ║
 * ║                                                                  ║
 * ║  [필터 체인 처리 순서]                                             ║
 * ║  Client → CORS → CSRF → JWT Filter → Authorization → Controller ║
 * ║                                                                  ║
 * ║  [설계 원칙]                                                      ║
 * ║  - Stateless 세션: JWT 기반 인증이므로 서버 측 세션 미사용         ║
 * ║  - CSRF 비활성화: REST API는 Cookie 기반이 아니므로 CSRF 불필요    ║
 * ║  - 역할 기반 접근 제어(RBAC): URL 패턴별 권한 설정                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Configuration
@EnableWebSecurity  // Spring Security 웹 보안 기능 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT 토큰 생성/검증을 담당하는 핵심 컴포넌트
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 🔐 HTTP 보안 필터 체인 설정
     *
     * Spring Security 6.x에서는 WebSecurityConfigurerAdapter가 폐기(deprecated)되었으므로
     * SecurityFilterChain을 Bean으로 등록하는 컴포넌트 기반 방식을 사용합니다.
     *
     * @param http HttpSecurity 객체 — HTTP 보안 설정 빌더
     * @return 구성 완료된 SecurityFilterChain 인스턴스
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ─────────────────────────────────────────────────
            // 1. CSRF(Cross-Site Request Forgery) 비활성화
            // REST API는 토큰 기반 인증을 사용하며, 브라우저의 쿠키/세션에
            // 의존하지 않으므로 CSRF 공격에 노출되지 않습니다.
            // ─────────────────────────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ─────────────────────────────────────────────────
            // 2. 세션 관리: STATELESS 모드
            // JWT 토큰 기반 인증에서는 서버가 세션을 생성/유지하지 않습니다.
            // 모든 인증 정보는 클라이언트가 보내는 JWT에 담겨 있습니다.
            //
            // SessionCreationPolicy 옵션:
            // - ALWAYS: 항상 세션 생성
            // - IF_REQUIRED: 필요 시 생성 (기본값)
            // - NEVER: 세션을 생성하지 않지만 존재하면 사용
            // - STATELESS: 세션을 절대 생성/사용하지 않음 ← JWT에 적합
            // ─────────────────────────────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ─────────────────────────────────────────────────
            // 3. URL 패턴별 접근 권한 설정 (RBAC: Role-Based Access Control)
            //
            // [설계 기준]
            // - /api/auth/** : 로그인, 회원가입 등 → 누구나 접근 가능
            // - /api/admin/** : 관리자 전용 API → ADMIN 역할만 접근 가능
            // - /api/public/** : 공개 API → 인증 없이 접근 가능
            // - 그 외 모든 요청 → 인증(JWT) 필수
            // ─────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // 인증 관련 엔드포인트: 로그인, 회원가입, 토큰 갱신
                .requestMatchers("/api/auth/**").permitAll()

                // 공개 엔드포인트: 헬스체크, API 문서 등
                .requestMatchers("/api/public/**", "/actuator/health").permitAll()

                // 관리자 전용 엔드포인트: ADMIN 역할이 있는 사용자만 접근
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 위에서 명시하지 않은 나머지 모든 요청은 인증 필수
                .anyRequest().authenticated()
            )

            // ─────────────────────────────────────────────────
            // 4. JWT 인증 필터 등록
            // UsernamePasswordAuthenticationFilter 전에 JWT 필터를 배치합니다.
            //
            // [동작 원리]
            // 모든 요청이 들어오면 JWT 필터가 먼저 실행되어:
            //   ① Authorization 헤더에서 Bearer 토큰 추출
            //   ② 토큰 유효성 검증 (서명, 만료일, 블랙리스트 확인)
            //   ③ 유효하면 SecurityContext에 Authentication 객체 저장
            //   ④ 이후 Spring Security의 일반적인 인가 흐름 진행
            // ─────────────────────────────────────────────────
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * 🔒 비밀번호 암호화 인코더
     *
     * BCrypt 알고리즘을 사용하여 비밀번호를 안전하게 해싱합니다.
     *
     * [BCrypt의 장점]
     * 1. 솔트(Salt) 내장: 같은 비밀번호라도 매번 다른 해시값 생성 → Rainbow Table 공격 방어
     * 2. 적응형 함수: strength 파라미터로 연산 비용 조절 가능 → 하드웨어 발전에 대응
     * 3. 업계 표준: Spring Security, Django 등 주요 프레임워크에서 기본 채택
     *
     * strength 10 (기본값): 약 100ms 소요 → 로그인 시 사용자가 느끼지 못하는 수준
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
