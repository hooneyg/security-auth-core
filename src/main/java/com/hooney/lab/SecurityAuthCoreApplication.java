package com.hooney.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              🔐 Security Auth Core Application                  ║
 * ║                                                                  ║
 * ║  엔터프라이즈 급 보안 인증 시스템의 메인 엔트리 포인트입니다.        ║
 * ║                                                                  ║
 * ║  이 애플리케이션은 다음 핵심 모듈을 포함합니다:                     ║
 * ║  1. JWT 토큰 발급/검증 (Access Token + Refresh Token)            ║
 * ║  2. OAuth2 소셜 로그인 (Google, Kakao)                           ║
 * ║  3. RSA + AES-256 하이브리드 암호화                               ║
 * ║  4. Redis 기반 토큰 블랙리스트 관리                               ║
 * ║                                                                  ║
 * ║  @SpringBootApplication 어노테이션은 다음 3가지를 결합한 것:       ║
 * ║  - @Configuration: 이 클래스 자체가 Spring Bean 설정 소스          ║
 * ║  - @EnableAutoConfiguration: 클래스패스 기반 자동 설정 활성화      ║
 * ║  - @ComponentScan: 하위 패키지의 @Component 자동 탐색             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@SpringBootApplication
public class SecurityAuthCoreApplication {

    public static void main(String[] args) {
        // SpringApplication.run()은 내부적으로 다음 순서로 동작합니다:
        // 1. ApplicationContext(IoC 컨테이너) 생성
        // 2. @ComponentScan으로 Bean 탐색 및 등록
        // 3. AutoConfiguration 적용 (Security, Redis 등)
        // 4. 내장 톰캣 서버 시작
        // 5. ApplicationReadyEvent 발행
        SpringApplication.run(SecurityAuthCoreApplication.class, args);
    }
}
