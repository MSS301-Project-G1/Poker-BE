package com.msspoker.shopservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.config.SharedAssetProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AssetUrlSignerTest {

    private static final String BUCKET_URL = "https://team-bucket.s3.ap-southeast-1.amazonaws.com/";
    private static final String CARD = BUCKET_URL + "poker/shop/seed/cards/mock-deck/AS.png";

    private final ObjectMapper objectMapper = new ObjectMapper();
    // Fake keys: presigning is computed locally, so no request reaches AWS.
    private final AssetUrlSigner signer = new AssetUrlSigner(new SharedAssetProperties("team-bucket", "ap-southeast-1",
            "AKIAEXAMPLEKEY000000", "example-secret-key", "poker/shop/", Duration.ofHours(6), false), objectMapper);

    @Test
    @DisplayName("Ký link trong poker/shop/ của bucket chung thành presigned GET sống theo url-ttl")
    void signsLinksUnderPrefix() {
        String signed = signer.signUrl(CARD);

        assertThat(signed).startsWith(CARD + "?");
        assertThat(signed).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=21600",
                "X-Amz-Credential=AKIAEXAMPLEKEY000000", "X-Amz-Signature=");
    }

    @Test
    @DisplayName("Giữ nguyên link ngoài bucket, ngoài poker/shop/ (vd avatar riêng tư) hoặc có '..'")
    void leavesOtherLinksUnchanged() {
        String avatar = BUCKET_URL + "poker/avatars/users/a/b.png";
        String traversal = BUCKET_URL + "poker/shop/../avatars/users/a/b.png";
        String minio = "http://localhost:9000/poker-assets/poker/sounds/x.mp3";

        assertThat(signer.signUrl(avatar)).isEqualTo(avatar);
        assertThat(signer.signUrl(traversal)).isEqualTo(traversal);
        assertThat(signer.signUrl(minio)).isEqualTo(minio);
        assertThat(signer.signUrl(null)).isNull();
    }

    @Test
    @DisplayName("Link đã lưu sẵn dạng presigned (sẽ hết hạn) được ký lại từ key")
    void resignsStoredPresignedLinks() {
        String stale = CARD + "?X-Amz-Expires=604800&X-Amz-Signature=old";

        String signed = signer.signUrl(stale);

        assertThat(signed).startsWith(CARD + "?").contains("X-Amz-Expires=21600").doesNotContain("Signature=old");
    }

    @Test
    @DisplayName("Ký mọi link bucket lồng trong metadata, giữ nguyên các giá trị khác")
    void signsMetadataLinks() throws Exception {
        String metadata = """
                {"skinType":"CARD_DECK_53","cardBackUrl":"%s","cards":{"AS":"%s"},"poses":["%s"],"soundUrl":null}
                """.formatted(CARD, CARD, CARD);

        JsonNode signed = objectMapper.readTree(signer.signMetadata(metadata));

        assertThat(signed.get("skinType").asText()).isEqualTo("CARD_DECK_53");
        assertThat(signed.get("cardBackUrl").asText()).startsWith(CARD + "?").contains("X-Amz-Signature=");
        assertThat(signed.get("cards").get("AS").asText()).startsWith(CARD + "?");
        assertThat(signed.get("poses").get(0).asText()).startsWith(CARD + "?");
        assertThat(signed.get("soundUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("Metadata không phải JSON hoặc không chứa link bucket được trả nguyên văn")
    void leavesOtherMetadataUnchanged() {
        assertThat(signer.signMetadata("not json " + BUCKET_URL)).isEqualTo("not json " + BUCKET_URL);
        assertThat(signer.signMetadata("{\"felt\":\"#8B0000\"}")).isEqualTo("{\"felt\":\"#8B0000\"}");
        assertThat(signer.signMetadata(null)).isNull();
    }

    @Test
    @DisplayName("Thư mục đã public: trả link thường cố định (bỏ chữ ký cũ), không cần key, link khác giữ nguyên")
    void returnsPlainLinksWhenPublic() throws Exception {
        AssetUrlSigner publicSigner = new AssetUrlSigner(new SharedAssetProperties("team-bucket", "ap-southeast-1",
                "", "", "poker/shop/", Duration.ofHours(6), true), objectMapper);
        String avatar = BUCKET_URL + "poker/avatars/users/a/b.png";
        String metadata = "{\"cards\":{\"AS\":\"" + CARD + "?X-Amz-Signature=old\"},\"felt\":\"#8B0000\"}";

        assertThat(publicSigner.signUrl(CARD)).isEqualTo(CARD);
        assertThat(publicSigner.signUrl(CARD + "?X-Amz-Expires=604800&X-Amz-Signature=old")).isEqualTo(CARD);
        assertThat(publicSigner.signUrl(avatar)).isEqualTo(avatar);
        JsonNode plain = objectMapper.readTree(publicSigner.signMetadata(metadata));
        assertThat(plain.get("cards").get("AS").asText()).isEqualTo(CARD);
        assertThat(plain.get("felt").asText()).isEqualTo("#8B0000");
    }

    @Test
    @DisplayName("Chưa cấu hình key: mọi link giữ nguyên")
    void passesThroughWhenNotConfigured() {
        AssetUrlSigner unconfigured = new AssetUrlSigner(new SharedAssetProperties("team-bucket", "ap-southeast-1",
                "", "", "poker/shop/", Duration.ofHours(6), false), objectMapper);
        String metadata = "{\"cardBackUrl\":\"" + CARD + "\"}";

        assertThat(unconfigured.signUrl(CARD)).isEqualTo(CARD);
        assertThat(unconfigured.signMetadata(metadata)).isEqualTo(metadata);
    }
}
