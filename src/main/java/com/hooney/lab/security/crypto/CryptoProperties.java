package com.hooney.lab.security.crypto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🔐 암호화 설정 프로퍼티 바인딩 클래스                      ║
 * ║                                                                  ║
 * ║  application.yml의 'encryption' 섹션 값을 Java 객체로 매핑합니다.  ║
 * ║                                                                  ║
 * ║  [바인딩 매핑]                                                    ║
 * ║  encryption.rsa.key-size       → this.rsa.keySize               ║
 * ║  encryption.rsa.public-key-path → this.rsa.publicKeyPath        ║
 * ║  encryption.aes.key-size       → this.aes.keySize               ║
 * ║  encryption.aes.algorithm      → this.aes.algorithm             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "encryption")
public class CryptoProperties {

    private Rsa rsa = new Rsa();
    private Aes aes = new Aes();

    @Getter
    @Setter
    public static class Rsa {
        /**
         * RSA 키 크기 (비트)
         * - 2048: 현재 업계 표준 (NIST 권장, 2030년까지 안전)
         * - 4096: 더 강력하지만 성능 비용이 4~8배 높음
         */
        private int keySize = 2048;

        /** RSA 공개키 파일 경로 (PEM 형식) */
        private String publicKeyPath;

        /** RSA 개인키 파일 경로 (PEM 형식, 절대 외부 노출 금지!) */
        private String privateKeyPath;
    }

    @Getter
    @Setter
    public static class Aes {
        /**
         * AES 키 크기 (비트)
         * - 128: 기본 보안 수준
         * - 256: 최고 보안 수준 (금융, 의료, 군사 등)
         */
        private int keySize = 256;

        /**
         * AES 암호화 알고리즘/모드/패딩
         * - AES/GCM/NoPadding: 암호화 + 무결성 검증 동시 수행 (권장)
         * - AES/CBC/PKCS5Padding: 레거시 호환, Padding Oracle Attack 취약
         */
        private String algorithm = "AES/GCM/NoPadding";
    }
}
