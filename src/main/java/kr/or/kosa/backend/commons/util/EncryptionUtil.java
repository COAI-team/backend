package kr.or.kosa.backend.commons.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";

    @Value("${jasypt.encryptor.password:defaultSecretKey123}") // Fallback default for dev
    private String secretKey;

    public String encrypt(String value) {
        if (value == null)
            return null;
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(getFixedKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null)
            return null;
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(getFixedKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private byte[] getFixedKey() {
        // Ensure key is 16 bytes for AES-128
        byte[] keyBytes = new byte[16];
        byte[] source = secretKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(source, 0, keyBytes, 0, Math.min(source.length, keyBytes.length));
        return keyBytes;
    }
}
