package com.hooney.lab.security.crypto;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🧪 HybridCryptoService 단위 테스트                       ║
 * ║                                                                  ║
 * ║  [테스트 범위]                                                    ║
 * ║  1. RSA 키 페어 생성 검증                                        ║
 * ║  2. 공개키/개인키 Base64 인코딩/디코딩 라운드트립                  ║
 * ║  3. 하이브리드 암호화 → 복호화 라운드트립 (데이터 무결성 검증)     ║
 * ║  4. 다양한 데이터 크기에 대한 암복호화 검증                       ║
 * ║  5. 잘못된 키로 복호화 시 예외 발생 확인                          ║
 * ║  6. 동일 평문의 반복 암호화 시 매번 다른 암호문 생성 확인          ║
 * ║                                                                  ║
 * ║  [테스트 전략]                                                    ║
 * ║  - Spring Context 없이 순수 단위 테스트                           ║
 * ║  - 각 테스트에서 독립적인 키 페어를 사용                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("🔐 Hybrid Crypto Service 테스트")
class HybridCryptoServiceTest {

    private HybridCryptoService cryptoService;
    private CryptoProperties cryptoProperties;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        // CryptoProperties 수동 생성 (기본값 사용: RSA-2048, AES-256-GCM)
        cryptoProperties = new CryptoProperties();

        // HybridCryptoService 인스턴스 생성
        cryptoService = new HybridCryptoService(cryptoProperties);

        // 테스트용 RSA 키 페어 생성
        keyPair = cryptoService.generateRsaKeyPair();
    }

    @Test
    @Order(1)
    @DisplayName("✅ RSA 키 페어 생성 — 공개키와 개인키가 정상 생성되어야 한다")
    void generateRsaKeyPair_ShouldReturnValidKeyPair() {
        // Then: 공개키와 개인키 모두 null이 아니어야 함
        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();

        // RSA 알고리즘으로 생성되었는지 확인
        assertThat(keyPair.getPublic().getAlgorithm()).isEqualTo("RSA");
        assertThat(keyPair.getPrivate().getAlgorithm()).isEqualTo("RSA");

        System.out.println("✅ RSA 키 페어 생성 성공");
        System.out.println("   공개키 알고리즘: " + keyPair.getPublic().getAlgorithm());
        System.out.println("   공개키 크기: " + keyPair.getPublic().getEncoded().length + " bytes");
    }

    @Test
    @Order(2)
    @DisplayName("✅ 키 인코딩/디코딩 라운드트립 — Base64 변환 후 원본 키와 동일해야 한다")
    void encodeAndDecodeKeys_ShouldPreserveKeyData() throws Exception {
        // Given: 공개키, 개인키를 Base64로 인코딩
        String encodedPublic = cryptoService.encodePublicKey(keyPair.getPublic());
        String encodedPrivate = cryptoService.encodePrivateKey(keyPair.getPrivate());

        // When: Base64에서 다시 키 객체로 복원
        var decodedPublic = cryptoService.decodePublicKey(encodedPublic);
        var decodedPrivate = cryptoService.decodePrivateKey(encodedPrivate);

        // Then: 원본 키와 복원된 키가 동일해야 함
        assertThat(decodedPublic.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(decodedPrivate.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());

        System.out.println("✅ 키 인코딩/디코딩 라운드트립 성공");
    }

    @Test
    @Order(3)
    @DisplayName("✅ 하이브리드 암복호화 라운드트립 — 암호화 후 복호화하면 원본과 동일해야 한다")
    void encryptAndDecrypt_ShouldReturnOriginalText() throws Exception {
        // Given: 암호화할 원본 평문
        String originalText = "이것은 금융 거래 데이터입니다. 계좌번호: 123-456-789, 거래금액: 1,000,000원";

        // When: 하이브리드 암호화 수행
        var encrypted = cryptoService.encrypt(originalText, keyPair.getPublic());

        // Then: 암호화 결과 객체의 모든 필드가 존재
        assertThat(encrypted.getEncryptedData()).isNotNull().isNotBlank();
        assertThat(encrypted.getEncryptedAesKey()).isNotNull().isNotBlank();
        assertThat(encrypted.getIv()).isNotNull().isNotBlank();

        // When: 하이브리드 복호화 수행
        String decrypted = cryptoService.decrypt(encrypted, keyPair.getPrivate());

        // Then: 복호화된 데이터가 원본과 완전히 동일
        assertThat(decrypted).isEqualTo(originalText);

        System.out.println("✅ 하이브리드 암복호화 라운드트립 성공");
        System.out.println("   원본: " + originalText);
        System.out.println("   암호문 (일부): " + encrypted.getEncryptedData().substring(0, 30) + "...");
        System.out.println("   복호문: " + decrypted);
    }

    @Test
    @Order(4)
    @DisplayName("✅ 대용량 데이터 암복호화 — 긴 문자열도 정상 처리되어야 한다")
    void encryptAndDecrypt_WithLargeData_ShouldWork() throws Exception {
        // Given: 1KB 이상의 대용량 테스트 데이터
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("대한민국 만세! Enterprise Security System Line ").append(i).append("\n");
        }
        String largeText = sb.toString();

        // When: 암호화 → 복호화
        var encrypted = cryptoService.encrypt(largeText, keyPair.getPublic());
        String decrypted = cryptoService.decrypt(encrypted, keyPair.getPrivate());

        // Then: 원본과 동일
        assertThat(decrypted).isEqualTo(largeText);

        System.out.println("✅ 대용량 데이터 (" + largeText.length() + " chars) 암복호화 성공");
    }

    @Test
    @Order(5)
    @DisplayName("❌ 잘못된 개인키로 복호화 — 예외가 발생해야 한다")
    void decrypt_WithWrongKey_ShouldThrowException() throws Exception {
        // Given: 키 페어 A로 암호화
        String originalText = "비밀 데이터";
        var encrypted = cryptoService.encrypt(originalText, keyPair.getPublic());

        // Given: 다른 키 페어 B 생성
        KeyPair wrongKeyPair = cryptoService.generateRsaKeyPair();

        // When & Then: 키 페어 B의 개인키로 복호화 시도 → 예외 발생
        assertThatThrownBy(() ->
                cryptoService.decrypt(encrypted, wrongKeyPair.getPrivate())
        ).isInstanceOf(Exception.class);

        System.out.println("✅ 잘못된 키로 복호화 시 예외 발생 확인");
    }

    @Test
    @Order(6)
    @DisplayName("✅ 동일 평문 반복 암호화 — 매번 다른 암호문이 생성되어야 한다 (IV 랜덤성)")
    void encrypt_SamePlainText_ShouldProduceDifferentCipherText() throws Exception {
        // Given: 동일한 평문
        String plainText = "동일한 평문 데이터";

        // When: 2번 암호화
        var encrypted1 = cryptoService.encrypt(plainText, keyPair.getPublic());
        var encrypted2 = cryptoService.encrypt(plainText, keyPair.getPublic());

        // Then: 암호문이 서로 달라야 함 (랜덤 AES 키 + 랜덤 IV 때문)
        assertThat(encrypted1.getEncryptedData())
                .isNotEqualTo(encrypted2.getEncryptedData());

        // But: 둘 다 복호화하면 동일한 원본이 나와야 함
        assertThat(cryptoService.decrypt(encrypted1, keyPair.getPrivate())).isEqualTo(plainText);
        assertThat(cryptoService.decrypt(encrypted2, keyPair.getPrivate())).isEqualTo(plainText);

        System.out.println("✅ 동일 평문에 대해 서로 다른 암호문 생성 확인 (IV 랜덤성 보장)");
    }

    @Test
    @Order(7)
    @DisplayName("✅ 빈 문자열 암복호화 — 빈 문자열도 정상 처리되어야 한다")
    void encryptAndDecrypt_WithEmptyString_ShouldWork() throws Exception {
        // Given: 빈 문자열
        String emptyText = "";

        // When: 암호화 → 복호화
        var encrypted = cryptoService.encrypt(emptyText, keyPair.getPublic());
        String decrypted = cryptoService.decrypt(encrypted, keyPair.getPrivate());

        // Then: 빈 문자열이 복원됨
        assertThat(decrypted).isEqualTo(emptyText);

        System.out.println("✅ 빈 문자열 암복호화 성공");
    }
}
