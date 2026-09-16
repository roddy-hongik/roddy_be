package com.roddy.global.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter("test-token-secret-key");

    @Test
    @DisplayName("암호화한 값을 다시 원래대로 돌린다")
    void encryptsAndDecrypts() {
        String token = "gho_exampleGithubAccessToken1234567890";

        String encrypted = converter.convertToDatabaseColumn(token);

        assertThat(encrypted).isNotEqualTo(token);
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(token);
    }

    @Test
    @DisplayName("같은 값을 두 번 암호화해도 결과가 다르다")
    void producesDifferentCipherTextEachTime() {
        String token = "gho_exampleGithubAccessToken1234567890";

        String first = converter.convertToDatabaseColumn(token);
        String second = converter.convertToDatabaseColumn(token);

        // GCM 은 같은 키에 같은 IV 를 두 번 쓰면 안 된다. 저장할 때마다 IV 를 새로 만드는지 본다.
        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(token);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(token);
    }

    @Test
    @DisplayName("값이 없으면 그대로 비워 둔다")
    void keepsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("다른 키로는 풀 수 없다")
    void cannotDecryptWithAnotherKey() {
        String encrypted = converter.convertToDatabaseColumn("gho_token");
        EncryptedStringConverter otherConverter = new EncryptedStringConverter("another-secret-key");

        assertThatThrownBy(() -> otherConverter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화");
    }

    @Test
    @DisplayName("암호화 키가 없으면 뜨지 않는다")
    void requiresSecretKey() {
        assertThatThrownBy(() -> new EncryptedStringConverter("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token-secret-key");
    }
}
