package com.msspoker.shopservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonMergerTest {

    private MetadataJsonMerger merger;

    @BeforeEach
    void setUp() {
        merger = new MetadataJsonMerger(new ObjectMapper());
    }

    @Test
    @DisplayName("Gộp metadata mới nhưng bảo toàn trường soundUrl đã tồn tại")
    void testMergePreservesSoundUrl() {
        String existingJson = "{\"soundUrl\":\"http://s3/sound.mp3\",\"oldField\":\"value\"}";
        String incomingJson = "{\"skinType\":\"CARD_DECK_53\",\"cardBackUrl\":\"http://s3/back.png\"}";

        String merged = merger.merge(existingJson, incomingJson);

        assertThat(merger.getStringField(merged, "soundUrl")).isEqualTo("http://s3/sound.mp3");
        assertThat(merger.getStringField(merged, "skinType")).isEqualTo("CARD_DECK_53");
        assertThat(merger.getStringField(merged, "cardBackUrl")).isEqualTo("http://s3/back.png");
    }

    @Test
    @DisplayName("Set soundUrl vào json rỗng hoặc chưa có")
    void testSetStringField() {
        String result = merger.setStringField(null, "soundUrl", "http://s3/my_sound.mp3");
        assertThat(merger.getStringField(result, "soundUrl")).isEqualTo("http://s3/my_sound.mp3");
    }

    @Test
    @DisplayName("Remove soundUrl khỏi metadata")
    void testRemoveField() {
        String existingJson = "{\"soundUrl\":\"http://s3/sound.mp3\",\"skinType\":\"CARD_DECK_53\"}";
        String result = merger.removeField(existingJson, "soundUrl");

        assertThat(merger.getStringField(result, "soundUrl")).isNull();
        assertThat(merger.getStringField(result, "skinType")).isEqualTo("CARD_DECK_53");
    }
}
