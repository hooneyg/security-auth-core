package com.hooney.lab.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║             📦 내장 Redis(Embedded Redis) 테스트 설정              ║
 * ║                                                                  ║
 * ║  [설정 목적]                                                      ║
 * ║  테스트 실행 시 별도의 외부 Redis 서버(Docker 등)에 의존하지 않고,  ║
 * ║  인메모리 기반의 Embedded Redis를 자동으로 기동 및 종료합니다.    ║
 * ║  이를 통해 GitHub Actions 등 CI/CD 환경에서도                     ║
 * ║  완벽히 독립적이고 격리된 통합 테스트 환경을 제공합니다.          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@TestConfiguration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void startRedis() throws IOException {
        // 지정된 포트로 인메모리 Redis 서버를 기동합니다.
        redisServer = new RedisServer(redisPort);
        try {
            redisServer.start();
            log.info("✅ Embedded Redis Server Started on port: {}", redisPort);
        } catch (Exception e) {
            // CI 환경에서 포트 충돌 등의 예외 발생 시 로깅
            log.error("❌ Embedded Redis Start Failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            try {
                // 서버가 정상적으로 기동된 상태인지 확인 후 종료
                if (redisServer.isActive()) {
                    redisServer.stop();
                    log.info("🛑 Embedded Redis Server Stopped.");
                }
            } catch (Exception e) {
                log.error("❌ Embedded Redis 종료 중 오류 발생: {}", e.getMessage());
            }
        }
    }
}
