package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardDeckValidatorTest {

    private CardDeckValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CardDeckValidator();
    }

    @Test
    @DisplayName("extractCardCode: Trích xuất chính xác mã lá bài từ nhiều định dạng tên file khác nhau")
    void extractCardCode_success() {
        assertThat(validator.extractCardCode("2h.png")).isEqualTo("2H");
        assertThat(validator.extractCardCode("10s.webp")).isEqualTo("10S");
        assertThat(validator.extractCardCode("ad.jpg")).isEqualTo("AD");
        assertThat(validator.extractCardCode("kc.jpeg")).isEqualTo("KC");
        assertThat(validator.extractCardCode("back.png")).isEqualTo("BACK");
        assertThat(validator.extractCardCode("card_back.png")).isEqualTo("BACK");
        assertThat(validator.extractCardCode("deck_back.webp")).isEqualTo("BACK");
        assertThat(validator.extractCardCode("preview.png")).isEqualTo("PREVIEW");
        assertThat(validator.extractCardCode("joker.png")).isEqualTo("JOKER");

        // Đường dẫn lồng nhau
        assertThat(validator.extractCardCode("folder/sub/5C.png")).isEqualTo("5C");
        assertThat(validator.extractCardCode("folder\\sub\\9H.png")).isEqualTo("9H");

        // File không hợp lệ
        assertThat(validator.extractCardCode("readme.txt")).isNull();
        assertThat(validator.extractCardCode("script.sh")).isNull();
        assertThat(validator.extractCardCode("")).isNull();
        assertThat(validator.extractCardCode(null)).isNull();
    }

    @Test
    @DisplayName("validateCoreCards: Vượt qua khi có đủ 53 lá bài cốt lõi")
    void validateCoreCards_success() {
        Set<String> fullDeck = new HashSet<>(CardDeckValidator.CORE_CARD_CODES);
        assertThat(fullDeck).hasSize(53);

        // Không ném ngoại lệ
        validator.validateCoreCards(fullDeck);
    }

    @Test
    @DisplayName("validateCoreCards: Ném ngoại lệ khi thiếu lá bài")
    void validateCoreCards_missingCards_throwsException() {
        Set<String> incompleteDeck = new HashSet<>(CardDeckValidator.CORE_CARD_CODES);
        incompleteDeck.remove("AS");
        incompleteDeck.remove("BACK");

        assertThatThrownBy(() -> validator.validateCoreCards(incompleteDeck))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("AS")
                .hasMessageContaining("BACK");
    }

    @Test
    @DisplayName("validateZipEntry: Chặn đứng tấn công Zip Slip")
    void validateZipEntry_zipSlipDetected() {
        assertThatThrownBy(() -> validator.validateZipEntry("../../../etc/passwd"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Zip Slip");

        assertThatThrownBy(() -> validator.validateZipEntry("/root/secret.png"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Zip Slip");

        assertThatThrownBy(() -> validator.validateZipEntry("cards\\..\\back.png"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Zip Slip");

        // Hợp lệ
        validator.validateZipEntry("cards/2H.png");
        validator.validateZipEntry("BACK.png");
    }
}
