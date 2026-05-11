package com.hooney.lab.security.crypto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         🔐 하이브리드 암호화 서비스 (RSA + AES-256-GCM)           ║
 * ║                                                                  ║
 * ║  [하이브리드 암호화란?]                                           ║
 * ║  RSA(비대칭키)와 AES(대칭키)의 장점을 결합한 암호화 방식입니다.    ║
 * ║                                                                  ║
 * ║  [왜 하이브리드 방식을 사용하는가?]                                ║
 * ║  - RSA 단독: 보안은 강력하지만, 대용량 데이터 암호화 시 매우 느림  ║
 * ║  - AES 단독: 속도는 빠르지만, 키를 안전하게 전달하기 어려움        ║
 * ║  - 하이브리드: AES로 데이터 암호화(빠름) + RSA로 AES키 암호화(안전)║
 * ║                                                                  ║
 * ║  [암호화 프로세스]                                                ║
 * ║  ┌───────────────────────────────────────────────────────┐       ║
 * ║  │ 1. AES-256 대칭키를 무작위 생성                        │       ║
 * ║  │ 2. 이 AES 키로 원본 데이터를 암호화 (빠름, ~1GB/s)     │       ║
 * ║  │ 3. RSA 공개키로 AES 키를 암호화 (작은 데이터이므로 빠름) │       ║
 * ║  │ 4. [암호화된 AES키 + 암호화된 데이터 + IV]를 결합하여 전송│     ║
 * ║  └───────────────────────────────────────────────────────┘       ║
 * ║                                                                  ║
 * ║  [복호화 프로세스]                                                ║
 * ║  ┌───────────────────────────────────────────────────────┐       ║
 * ║  │ 1. RSA 개인키로 AES 키를 복호화                        │       ║
 * ║  │ 2. 복호화된 AES 키로 원본 데이터를 복호화               │       ║
 * ║  └───────────────────────────────────────────────────────┘       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridCryptoService {

    private final CryptoProperties cryptoProperties;

    /**
     * GCM(Galois/Counter Mode) 인증 태그 길이 (비트)
     *
     * GCM 모드는 암호화와 동시에 데이터 무결성을 검증하는 인증 태그를 생성합니다.
     * 128비트(16바이트)가 표준 길이이며, NIST에서 권장하는 최소값입니다.
     * 이 태그가 일치하지 않으면 데이터가 변조된 것으로 판단합니다.
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * GCM 초기화 벡터(IV) 길이 (바이트)
     *
     * IV(Initialization Vector)는 동일한 키로 동일한 평문을 암호화해도
     * 매번 다른 암호문이 생성되도록 보장하는 랜덤 값입니다.
     * GCM 모드에서는 12바이트(96비트) IV가 표준이며 최적의 성능을 보입니다.
     */
    private static final int GCM_IV_LENGTH = 12;

    // ═══════════════════════════════════════════════════════
    // 🔑 RSA 키 페어 관련 메서드
    // ═══════════════════════════════════════════════════════

    /**
     * 🔑 RSA 키 페어 생성
     *
     * RSA 공개키(Public Key)와 개인키(Private Key) 쌍을 생성합니다.
     *
     * [사용 시나리오]
     * 1. 최초 시스템 구축 시 이 메서드로 키 페어 생성
     * 2. 공개키는 클라이언트에 배포 (데이터 암호화용)
     * 3. 개인키는 서버에 안전하게 보관 (데이터 복호화용)
     *
     * @return RSA 공개키/개인키 쌍
     * @throws NoSuchAlgorithmException RSA 알고리즘을 사용할 수 없는 경우
     */
    public KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        // KeyPairGenerator: Java 표준 비대칭키 생성기
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");

        // 키 크기 초기화 (application.yml의 encryption.rsa.key-size 값 사용)
        // SecureRandom: 암호학적으로 안전한 난수 생성기 (일반 Random과 다름)
        keyPairGenerator.initialize(cryptoProperties.getRsa().getKeySize(), new SecureRandom());

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        log.info("✅ RSA 키 페어 생성 완료 (키 크기: {} bits)", cryptoProperties.getRsa().getKeySize());

        return keyPair;
    }

    /**
     * 📤 공개키를 Base64 문자열로 변환
     *
     * 생성된 공개키를 저장하거나 전달하기 위해 Base64 인코딩합니다.
     * X.509 표준 형식으로 인코딩됩니다.
     *
     * @param publicKey RSA 공개키
     * @return Base64 인코딩된 공개키 문자열
     */
    public String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 📤 개인키를 Base64 문자열로 변환
     *
     * 생성된 개인키를 저장하기 위해 Base64 인코딩합니다.
     * PKCS#8 표준 형식으로 인코딩됩니다.
     *
     * @param privateKey RSA 개인키
     * @return Base64 인코딩된 개인키 문자열
     */
    public String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 📥 Base64 문자열에서 공개키 복원
     *
     * @param base64PublicKey Base64 인코딩된 공개키 문자열
     * @return RSA 공개키 객체
     */
    public PublicKey decodePublicKey(String base64PublicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * 📥 Base64 문자열에서 개인키 복원
     *
     * @param base64PrivateKey Base64 인코딩된 개인키 문자열
     * @return RSA 개인키 객체
     */
    public PrivateKey decodePrivateKey(String base64PrivateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    // ═══════════════════════════════════════════════════════
    // 🔒 하이브리드 암호화 / 복호화
    // ═══════════════════════════════════════════════════════

    /**
     * 🔒 하이브리드 암호화 (RSA + AES-256-GCM)
     *
     * 원본 데이터를 하이브리드 방식으로 암호화합니다.
     *
     * [처리 순서]
     * 1. AES-256 대칭키를 무작위로 생성
     * 2. 12바이트 IV(초기화 벡터)를 무작위로 생성
     * 3. AES-GCM으로 원본 데이터 암호화
     * 4. RSA 공개키로 AES 대칭키를 암호화
     * 5. 결과물을 Base64 인코딩하여 반환
     *
     * @param plainText 암호화할 원본 평문 데이터
     * @param publicKey RSA 공개키 (AES 키 암호화용)
     * @return HybridEncryptedData 객체 (암호화된 데이터, 암호화된 AES 키, IV)
     */
    public HybridEncryptedData encrypt(String plainText, PublicKey publicKey) throws Exception {

        // ── Step 1: AES-256 대칭키 무작위 생성 ──
        // 이 키는 이 한 번의 암호화에만 사용되고, RSA로 암호화되어 전달됩니다.
        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        aesKeyGen.init(cryptoProperties.getAes().getKeySize(), new SecureRandom());
        SecretKey aesKey = aesKeyGen.generateKey();

        // ── Step 2: GCM IV(초기화 벡터) 무작위 생성 ──
        // 매번 새로운 IV를 사용해야 동일 평문 + 동일 키로도 다른 암호문이 생성됩니다.
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        // ── Step 3: AES-GCM으로 데이터 암호화 ──
        Cipher aesCipher = Cipher.getInstance(cryptoProperties.getAes().getAlgorithm());
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] encryptedData = aesCipher.doFinal(plainText.getBytes("UTF-8"));

        // ── Step 4: RSA 공개키로 AES 키 암호화 ──
        // AES 키(32바이트)는 작은 데이터이므로 RSA로 암호화해도 성능 문제 없음
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

        // ── Step 5: 결과물 조립 ──
        HybridEncryptedData result = new HybridEncryptedData();
        result.setEncryptedData(Base64.getEncoder().encodeToString(encryptedData));
        result.setEncryptedAesKey(Base64.getEncoder().encodeToString(encryptedAesKey));
        result.setIv(Base64.getEncoder().encodeToString(iv));

        log.debug("🔒 하이브리드 암호화 완료 (원본: {} bytes → 암호문: {} bytes)",
                plainText.length(), encryptedData.length);

        return result;
    }

    /**
     * 🔓 하이브리드 복호화 (RSA + AES-256-GCM)
     *
     * 암호화된 데이터를 원본으로 복원합니다.
     *
     * [처리 순서]
     * 1. Base64 디코딩 (문자열 → 바이트 배열)
     * 2. RSA 개인키로 AES 대칭키 복호화
     * 3. 복원된 AES 키와 IV로 데이터 복호화
     * 4. GCM 인증 태그 검증 (데이터 무결성 확인)
     *
     * @param encryptedPayload 암호화된 데이터 객체
     * @param privateKey       RSA 개인키 (AES 키 복호화용)
     * @return 복호화된 원본 평문
     */
    public String decrypt(HybridEncryptedData encryptedPayload, PrivateKey privateKey) throws Exception {

        // ── Step 1: Base64 디코딩 ──
        byte[] encryptedAesKey = Base64.getDecoder().decode(encryptedPayload.getEncryptedAesKey());
        byte[] encryptedData = Base64.getDecoder().decode(encryptedPayload.getEncryptedData());
        byte[] iv = Base64.getDecoder().decode(encryptedPayload.getIv());

        // ── Step 2: RSA 개인키로 AES 키 복호화 ──
        // OAEP 패딩: RSA 암호화 시 사용한 것과 동일한 패딩 방식 사용
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        // ── Step 3: AES-GCM으로 데이터 복호화 ──
        // GCM 모드는 복호화 시 자동으로 인증 태그를 검증합니다.
        // 태그가 일치하지 않으면 AEADBadTagException이 발생 → 데이터 변조 감지
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        Cipher aesCipher = Cipher.getInstance(cryptoProperties.getAes().getAlgorithm());
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
        byte[] decryptedData = aesCipher.doFinal(encryptedData);

        String plainText = new String(decryptedData, "UTF-8");
        log.debug("🔓 하이브리드 복호화 완료 (암호문: {} bytes → 원본: {} bytes)",
                encryptedData.length, plainText.length());

        return plainText;
    }

    // ═══════════════════════════════════════════════════════
    // 📦 암호화 결과 데이터 클래스
    // ═══════════════════════════════════════════════════════

    /**
     * 하이브리드 암호화 결과를 담는 데이터 클래스
     *
     * [구성 요소]
     * - encryptedData:   AES-GCM으로 암호화된 원본 데이터 (Base64 인코딩)
     * - encryptedAesKey: RSA 공개키로 암호화된 AES 대칭키 (Base64 인코딩)
     * - iv:              AES-GCM 초기화 벡터 (Base64 인코딩)
     *
     * 수신측은 이 3가지를 모두 가지고 있어야 복호화가 가능합니다.
     */
    @Getter
    @Setter
    public static class HybridEncryptedData {
        private String encryptedData;
        private String encryptedAesKey;
        private String iv;
    }
}
