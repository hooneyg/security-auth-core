package com.hooney.lab.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.Socket;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║             📦 내장 Redis(Embedded Redis) 테스트 설정              ║
 * ║                                                                  ║
 * ║  [설정 목적]                                                      ║
 * ║  테스트 실행 시 별도의 외부 Redis 서버(Docker 등)에 의존하지 않고,  ║
 * ║  인메모리 기반의 Embedded Redis를 자동으로 기동 및 종료합니다.    ║
 * ║                                                                  ║
 * ║  [개선 사항]                                                      ║
 * ║  여러 개의 Spring Context가 로드/언로드될 때 발생할 수 있는       ║
 * ║  포트 충돌 및 Redis 조기 종료(Connection Reset) 현상을 방지하고자, ║
 * ║  싱글톤 static 인스턴스 관리와 JVM Shutdown Hook 정책을 적용함.   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@TestConfiguration
public class EmbeddedRedisConfig {

    private static RedisServer redisServer;
    private static final Object lock = new Object();

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void startRedis() throws IOException {
        synchronized (lock) {
            if (redisServer == null) {
                // 이미 동일 포트가 사용 중인 경우 기동 생략 (외부 Redis 또는 이전 프로세스 재활용)
                if (isPortInUse(redisPort)) {
                    log.info("⚠️ Port {} is already in use. Skipping Embedded Redis start.", redisPort);
                    return;
                }

                redisServer = new RedisServer(redisPort);
                try {
                    redisServer.start();
                    log.info("✅ Embedded Redis Server Started on port: {}", redisPort);

                    // JVM 종료 시 최종적으로 단 한 번만 안전하게 종료되도록 Shutdown Hook 등록
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        synchronized (lock) {
                            if (redisServer != null && redisServer.isActive()) {
                                try {
                                    redisServer.stop();
                                    log.info("🛑 Embedded Redis Server Stopped via JVM Shutdown Hook.");
                                } catch (Exception e) {
                                    log.error("❌ Embedded Redis 종료 중 오류 발생: {}", e.getMessage());
                                }
                            }
                        }
                    }));
                } catch (Exception e) {
                    log.error("❌ Embedded Redis Start Failed: {}", e.getMessage());
                }
            } else {
                log.info("ℹ️ Embedded Redis Server is already running. Reusing the running instance.");
            }
        }
    }

    private boolean isPortInUse(int port) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
