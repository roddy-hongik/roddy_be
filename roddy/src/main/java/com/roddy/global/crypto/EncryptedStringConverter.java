package com.roddy.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 민감한 문자열을 DB 에 암호화해서 넣는다.
 *
 * <p>깃허브 액세스 토큰처럼 유출되면 사용자 계정에 접근할 수 있는 값에 쓴다. 애플리케이션 안에서는
 * 평문으로 다루고 저장할 때만 암호화하므로, 쓰는 쪽 코드는 암호화를 몰라도 된다.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(@Value("${app.security.token-secret-key}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.token-secret-key 가 필요합니다.");
        }
        this.secretKey = toKey(secret);
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            // GCM 은 같은 키에 같은 IV 를 두 번 쓰면 안 된다. 저장할 때마다 새로 만든다.
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 복호화에 IV 가 필요하므로 앞에 붙여 함께 저장한다. IV 는 비밀이 아니다.
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("값을 암호화하지 못했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encoded) {
        if (encoded == null) {
            return null;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("값을 복호화하지 못했습니다. 암호화 키가 바뀌었는지 확인이 필요합니다.", e);
        }
    }

    /** 설정값의 길이가 제각각이므로 해시를 거쳐 256비트 키로 만든다. */
    private SecretKey toKey(String secret) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hashed, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 키를 만들지 못했습니다.", e);
        }
    }
}
