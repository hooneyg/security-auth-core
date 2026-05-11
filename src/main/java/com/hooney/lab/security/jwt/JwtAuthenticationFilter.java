package com.hooney.lab.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🛡️ JWT 인증 필터 (모든 HTTP 요청 인터셉트)               ║
 * ║                                                                  ║
 * ║  [이 필터의 역할]                                                 ║
 * ║  Spring Security 필터 체인에 등록되어, 모든 HTTP 요청이 들어올 때  ║
 * ║  Authorization 헤더에서 JWT를 추출하고 유효성을 검증합니다.        ║
 * ║                                                                  ║
 * ║  [동작 흐름]                                                      ║
 * ║  ① HTTP Request 수신                                             ║
 * ║  ② Authorization 헤더에서 "Bearer {token}" 추출                   ║
 * ║  ③ JwtTokenProvider로 토큰 유효성 검증                            ║
 * ║  ④ 유효하면 → Authentication 객체 생성 → SecurityContext에 저장    ║
 * ║  ⑤ 무효하면 → SecurityContext 비워둠 → 이후 403/401 응답 반환     ║
 * ║  ⑥ 다음 필터로 요청 전달 (FilterChain.doFilter)                   ║
 * ║                                                                  ║
 * ║  [OncePerRequestFilter를 상속하는 이유]                           ║
 * ║  일반 Filter는 한 요청에 여러 번 실행될 수 있지만(Forward/Include  ║
 * ║  등으로 인해), OncePerRequestFilter는 요청당 딱 1번만 실행됩니다.  ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * HTTP 요청 헤더에서 JWT를 추출하는 데 사용되는 상수들
     *
     * [Bearer 스킴이란?]
     * RFC 6750에 정의된 OAuth 2.0 토큰 전달 방식입니다.
     * "Bearer"는 "이 토큰을 가진 사람(bearer)에게 접근을 허용하라"는 의미입니다.
     *
     * 실제 요청 예시:
     *   GET /api/users HTTP/1.1
     *   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 🔍 필터 핵심 로직
     *
     * @param request     현재 HTTP 요청
     * @param response    현재 HTTP 응답
     * @param filterChain 다음 필터로 요청을 전달하기 위한 체인
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1: Authorization 헤더에서 JWT 토큰 추출
        String token = resolveToken(request);

        // Step 2: 토큰이 존재하고 유효한 경우에만 인증 처리
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            // Step 3: 토큰에서 Authentication 객체 생성
            // Authentication 객체에는 사용자 ID, 권한(roles) 정보가 포함됨
            Authentication authentication = jwtTokenProvider.getAuthentication(token);

            // Step 4: SecurityContextHolder에 Authentication 설정
            // 이 시점부터 Spring Security는 이 요청을 "인증된 요청"으로 인식함
            // → @PreAuthorize, hasRole() 등의 인가 로직이 정상 동작
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("🔓 인증 성공 — 사용자: {}, 요청: {} {}",
                    authentication.getName(),
                    request.getMethod(),
                    request.getRequestURI()
            );
        }

        // Step 5: 다음 필터로 요청 전달
        // 토큰이 없거나 무효해도 여기서 에러를 던지지 않고 다음 필터로 넘김
        // → Spring Security의 ExceptionTranslationFilter가 401/403을 적절히 처리
        filterChain.doFilter(request, response);
    }

    /**
     * 📤 HTTP 요청 헤더에서 JWT 토큰 추출
     *
     * Authorization 헤더의 값에서 "Bearer " 접두사를 제거하고
     * 순수한 JWT 문자열만 반환합니다.
     *
     * @param request HTTP 요청
     * @return JWT 토큰 문자열 (없으면 null)
     *
     * 예시:
     *   헤더 값: "Bearer eyJhbGciOiJIUzUxMiJ9..."
     *   반환 값: "eyJhbGciOiJIUzUxMiJ9..."
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        // 헤더 값이 존재하고 "Bearer "로 시작하는지 확인
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            // "Bearer " (7글자) 이후의 순수 토큰 문자열만 추출
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
