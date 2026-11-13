package com.msspoker.chatservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msspoker.chatservice.config.ChatProperties;
import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.enums.ChatError;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.validator.ChatProfanityValidator;
import com.msspoker.chatservice.validator.ChatRequestValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ChatProfanityValidatorTest {
    private final ChatRequestValidator validator =
            new ChatRequestValidator(new ChatProperties(), new ChatProfanityValidator());

    ChatProfanityValidatorTest() throws IOException {}

    @ParameterizedTest
    @ValueSource(strings = {
        "địt mẹ", "DIT ME", "đụ má", "du ma", "ĐỊT", "lồn", "cặc", "buồi", "đĩ",
        "dcm", "vcl", "vkl", "clmm", "ditme", "địtmẹ", "fuck", "FUCK YOU",
        "ＦＵＣＫ", "f.u.c.k", "f u c k", "f u c k u", "f\u200buck", "f\u2060uck", "fuuuck",
        "sh1t", "$hit", "b1tch", "@sshole", "motherfucker", "fuck!!!", "xin chào, đéo",
        "đ\u0069\u0323t", "d.i.t m.e", "d c m", "Đ# mm @", "Đ#mm@", "đm m",
        "d# mm @", "f**uck", "sh!t", "đ#mm😀", "dm", "DM", "d m", "d#m", "dm@",
        "đù má", "đù mẹ", "đậu má", "đậu mẹ", "đệt mẹ", "đjt", "đỵt",
        "địt mẹ mày", "dit me may", "địt mịa", "dit mia", "đụ moẹ", "du moe",
        "đít mẹ", "đ mẹ", "đ me", "đ ma", "đờ mờ", "do mo", "đê mờ", "đờ cờ mờ",
        "dmmm", "đmmmm", "ddmm", "đđmm", "ddccmm", "đ# m# m@", "dd###mm", "clm", "c.l.m"
    })
    void rejectsBannedWordsAndCommonObfuscation(String content) {
        assertThatThrownBy(() -> validator.content(new SendMessageRequest("TEXT", content, null)))
                .isInstanceOfSatisfying(ChatApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ChatError.CHAT_CONTENT_NOT_ALLOWED);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @ParameterizedTest
    @MethodSource("abbreviationVariants")
    void rejectsAbbreviationsAcrossCaseSymbolsSpacingAndRepeatedLetters(String content) {
        assertThatThrownBy(() -> validator.content(new SendMessageRequest("TEXT", content, null)))
                .isInstanceOfSatisfying(ChatApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ChatError.CHAT_CONTENT_NOT_ALLOWED));
    }

    static Stream<String> abbreviationVariants() {
        return Stream.of("dm", "đm", "dcm", "đcm", "dkm", "đkm", "dmm", "đmm",
                        "dcmm", "đcmm", "dkmm", "đkmm", "clmm", "vl", "vcl", "vkl")
                .flatMap(word -> Stream.of(" ", ".", "-", "_", "#", "\u200b", "😀")
                        .flatMap(separator -> {
                            String spaced = word.codePoints()
                                    .mapToObj(code -> new String(Character.toChars(code)))
                                    .collect(Collectors.joining(separator));
                            String repeated = word.codePoints()
                                    .mapToObj(code -> new String(Character.toChars(code)).repeat(2))
                                    .collect(Collectors.joining(separator));
                            return Stream.of(spaced, "@" + spaced.toUpperCase(Locale.ROOT) + "#", repeated);
                        }));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Xin chào các bạn", "Con đi học rồi", "con di hoc roi", "Du lịch vui vẻ",
        "du lich", "Quả bóng lớn", "lon nuoc", "Các trận đấu", "cac ban", "Buổi tối tốt lành",
        "Dù mẹ chưa về", "classic", "Scunthorpe", "cocktail", "Dickens", "assistant",
        "We should shuffle the cards", "hello 😀", "m1", "a b c", "Đan mạch",
        "Tôi đang ở Đan Mạch", "f u n", "a s ship", "d c model", "admin", "badminton",
        "Dmitri", "đam mê", "đậm màu", "đi mua", "điểm mù", "rau má", "đậu xanh"
    })
    void allowsOrdinaryVietnameseAndWordsContainingBannedSubstrings(String content) {
        assertThatCode(() -> validator.content(new SendMessageRequest("TEXT", content, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesOriginalContentInsteadOfSavingNormalizedText() {
        assertThat(validator.content(new SendMessageRequest("TEXT", "  Xin CHÀO 😀!  ", null)))
                .isEqualTo("Xin CHÀO 😀!");
    }
}
