package com.eyki.offerpilot.common.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 加解密工具。
 *
 * <p>用于对用户自定义的 DeepSeek API Key 进行加密存储，防止数据库泄露导致 Key 被盗用。
 * 加密密钥通过环境变量 {@code API_KEY_ENCRYPTION_SECRET} 注入，要求 32 字节（256 位）。</p>
 *
 * <p>算法：AES/GCM/NoPadding，随机 12 字节 IV，附加 GCM 认证标签（16 字节）。</p>
 */
@Component
public class AesGcmEncryptor {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptor.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecretKey secretKey;

    /**
     * @param secretHex 加密密钥（64 字符十六进制字符串，对应 32 字节 AES-256）
     */
    public AesGcmEncryptor(@Value("${offer-pilot.api-key-encryption.secret}") String secretHex) {
        if (secretHex == null || secretHex.isBlank()) {
            log.warn("API Key 加密密钥未配置（offer-pilot.api-key-encryption.secret），"
                + "API Key 将以明文存储！请设置环境变量 API_KEY_ENCRYPTION_SECRET");
            this.secretKey = null;
            return;
        }
        byte[] decoded = hexToBytes(secretHex);
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                "加密密钥必须为 32 字节（256 位），当前 " + decoded.length + " 字节");
        }
        this.secretKey = new SecretKeySpec(decoded, "AES");
        log.info("AES-256-GCM 加密器初始化完成");
    }

    /**
     * 加密明文。
     *
     * @param plainText 明文（UTF-8 编码）
     * @return Base64 编码的密文（IV + ciphertext + tag）
     */
    public String encrypt(String plainText) {
        if (secretKey == null) {
            log.warn("加密密钥未配置，API Key 明文存储");
            return plainText;
        }
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            // new SecureRandom() 使用 /dev/urandom（非阻塞），避免 headless 服务启动时挂起
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 拼接 IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("AES-256-GCM 加密失败", e);
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param encrypted Base64 编码的密文（IV + ciphertext + tag）
     * @return 明文
     */
    public String decrypt(String encrypted) {
        if (secretKey == null) {
            log.warn("加密密钥未配置，直接返回存储值");
            return encrypted;
        }
        if (encrypted == null) {
            return null;
        }
        // 如果明文存储（未加密），直接返回
        if (!encrypted.startsWith("sk-")) {
            // 可能是未加密的旧数据，尝试解密，失败则原样返回
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AES-256-GCM 解密失败，尝试作为明文返回: {}", e.getMessage());
            // 兼容旧数据：如果解密失败，可能是未加密的，原样返回
            return encrypted;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}