package com.warbox.creator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HandlePolicyTest {

    @Test
    void normalizeTrimsAndLowercases() {
        assertThat(HandlePolicy.normalize("  Davi_Silva ")).isEqualTo("davi_silva");
        assertThat(HandlePolicy.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a1b", "davi_silva", "davi-silva", "abcdefghijklmnopqrstuvwxyz1234"})
    void acceptsValidFormats(String handle) {
        assertThat(HandlePolicy.isValidFormat(handle)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstuvwxyz12345", "-abc", "abc-", "_abc", "ABC", "joão",
            "a b", "a.b", "a/b", ""})
    void rejectsInvalidFormats(String handle) {
        assertThat(HandlePolicy.isValidFormat(handle)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(HandlePolicy.isValidFormat(null)).isFalse();
    }

    @Test
    void reservedWords() {
        assertThat(HandlePolicy.isReserved("me")).isTrue();
        assertThat(HandlePolicy.isReserved("admin")).isTrue();
        assertThat(HandlePolicy.isReserved("explorar")).isTrue();
        assertThat(HandlePolicy.isReserved("davi")).isFalse();
    }
}
