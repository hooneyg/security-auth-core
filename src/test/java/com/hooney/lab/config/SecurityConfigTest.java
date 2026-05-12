package com.hooney.lab.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                 🛡️ SecurityConfig 통합 테스트                   ║
 * ║                                                                  ║
 * ║  [테스트 목적]                                                    ║
 * ║  Spring Security의 Filter Chain이 의도한 대로 동작하는지 검증.    ║
 * ║  특히 인증(Authentication)과 인가(Authorization) 규칙이           ║
 * ║  엔드포인트별로 정확히 적용되었는지 확인합니다.                   ║
 * ║                                                                  ║
 * ║  [주요 검증 항목]                                                 ║
 * ║  1. 공개 엔드포인트(/api/v1/auth/**)의 익명 접근 허용 여부        ║
 * ║  2. 보호된 엔드포인트(/api/v1/users)의 미인증 접근 차단 여부      ║
 * ║  3. 위변조되거나 잘못된 JWT 토큰 삽입 시 차단 여부                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedRedisConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인가 우회 테스트: 인증 없이 공개 엔드포인트(/api/v1/auth/login)에 접근하면 401/403이 발생하지 않아야 한다")
    void whenAccessPublicEndpointWithoutAuth_thenShouldNotBeBlocked() throws Exception {
        // given & when
        // 공개된 엔드포인트(로그인 등)에 토큰 없이 POST 요청을 보냅니다.
        mockMvc.perform(post("/api/v1/auth/login"))
                // then
                // 더미 컨트롤러가 없다면 404(Not Found)가 반환될 수 있으나,
                // 중요한 것은 Security 필터에 의해 401(Unauthorized)이나 403(Forbidden)으로 
                // 차단되지 않았다는 것을 검증하는 것입니다.
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    boolean isBlockedBySecurity = (statusCode == 401 || statusCode == 403);
                    assert !isBlockedBySecurity : "공개 엔드포인트가 Security 필터에 의해 차단되었습니다. (Status: " + statusCode + ")";
                });
    }

    @Test
    @DisplayName("인가 차단 테스트: 인증 없이 보호된 엔드포인트(/api/v1/users)에 접근하면 401 Unauthorized가 발생해야 한다")
    void whenAccessProtectedEndpointWithoutAuth_thenUnauthorized() throws Exception {
        // given & when
        // 인증이 필요한 보호된 엔드포인트에 토큰 없이 GET 요청을 보냅니다.
        mockMvc.perform(get("/api/v1/users"))
                // then
                // JwtAuthenticationFilter를 통과하지 못하고,
                // AuthenticationEntryPoint에 의해 401 에러 응답이 반환되어야 합니다.
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인가 차단 테스트: 잘못된 형식의 JWT 토큰으로 접근하면 401 Unauthorized가 발생해야 한다")
    void whenAccessWithInvalidJwt_thenUnauthorized() throws Exception {
        // given
        // 고의로 조작된(위변조된) 형태의 토큰 문자열을 준비합니다.
        String invalidToken = "Bearer eyJhbGciOiJIUzUxMiJ9.invalid.payload.signature";

        // when
        // Authorization 헤더에 잘못된 토큰을 넣고 보호된 엔드포인트에 접근합니다.
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", invalidToken))
                // then
                // JwtTokenProvider의 서명 검증 로직에서 예외가 발생하고,
                // 최종적으로 401 Unauthorized 에러가 반환되어야 합니다.
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS Preflight 테스트: 허용된 출처(Origin)의 OPTIONS 요청에 대해 CORS 헤더가 포함된 200 OK 응답을 반환해야 한다")
    void whenCorsPreflightRequest_thenOk() throws Exception {
        // given & when
        // 브라우저가 실제 요청을 보내기 전, 서버가 허용하는지 확인하는 프리플라이트(OPTIONS) 요청 시뮬레이션
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                // then
                // CORS 설정에 의해 허용되어 200 응답과 함께 Access-Control-Allow-Origin 헤더가 반환되어야 합니다.
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                    assert "http://localhost:3000".equals(allowOrigin) : "CORS 헤더가 누락되거나 Origin이 일치하지 않습니다.";
                });
    }
}
