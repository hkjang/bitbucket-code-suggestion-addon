package com.jask.bitbucket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * API 키 등 자격증명을 AES-256-GCM으로 암호화/복호화합니다.
 * 암호화된 값은 ENC: 접두사와 함께 Base64 인코딩되어 저장됩니다.
 */
public final class CredentialEncryptor {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptor.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final String ENCRYPTED_PREFIX = "ENC:";

    // 노드별 고정 시드 — 실 운영에서는 환경변수/파일로 외부화 권장
    private static final String DEFAULT_PASSPHRASE = "jask-bitbucket-plugin-key-v1";

    private CredentialEncryptor() {}

    /**
     * 평문을 암호화합니다.
     *
     * @param plainText 평문
     * @return ENC: 접두사가 붙은 암호화된 문자열 (Base64)
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText; // 이미 암호화된 값
        }

        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            SecretKey key = deriveKey(DEFAULT_PASSPHRASE, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

            // salt + iv + cipherText를 하나로 결합
            ByteBuffer buffer = ByteBuffer.allocate(SALT_LENGTH + GCM_IV_LENGTH + cipherText.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(cipherText);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("자격증명 암호화 실패: {}", e.getMessage());
            throw new RuntimeException("자격증명 암호화 실패", e);
        }
    }

    /**
     * 암호화된 문자열을 복호화합니다.
     *
     * @param encryptedText ENC: 접두사가 붙은 암호화된 문자열
     * @return 복호화된 평문
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        if (!isEncrypted(encryptedText)) {
            return encryptedText; // 평문 그대로 반환 (마이그레이션 호환)
        }

        try {
            String base64 = encryptedText.substring(ENCRYPTED_PREFIX.length());
            byte[] decoded = Base64.getDecoder().decode(base64);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] salt = new byte[SALT_LENGTH];
            buffer.get(salt);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            SecretKey key = deriveKey(DEFAULT_PASSPHRASE, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, "UTF-8");
        } catch (Exception e) {
            log.error("자격증명 복호화 실패: {}", e.getMessage());
            throw new RuntimeException("자격증명 복호화 실패", e);
        }
    }

    /**
     * 해당 값이 암호화된 값인지 확인합니다.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private static SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
