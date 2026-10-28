package com.msspoker.shopservice.datainit;

import com.msspoker.shopservice.enums.ProductTier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Fixed ids and bucket keys of the seed products. The files were uploaded once to
 * {@code shop.data-init.asset-base-url}; keys here are relative to that folder. They carry
 * StorageKeyConstants.CACHE_CONTROL, so browsers keep them for a year: changed art needs a new key.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SeedProducts {

    static final UUID MOCK_DECK_ID = UUID.fromString("01a18038-b7b6-7820-a471-179ef1e6a302");
    static final UUID TAUNT_SOUND_ID = UUID.fromString("01a18038-b7b9-75f1-a8a2-175161315b48");

    /** Holds {CODE}.png for the 52 faces and BACK, as CardDeckValidator names them. */
    static final String MOCK_DECK_FOLDER = "cards/mock-deck/";
    static final String CARD_IMAGE_EXTENSION = ".png";
    static final String MOCK_DECK_PREVIEW_CODE = "AS";
    /** Same skin type the ZIP upload pipeline writes, so clients read both alike. */
    static final String DECK_SKIN_TYPE = "CARD_DECK_53";
    static final String DECK_VERSION = "seed-v1";

    static final String TAUNT_SOUND_ICON = "sounds/cho-cao-bang/icon.webp";
    static final String TAUNT_SOUND_FILE = "sounds/cho-cao-bang/sound.mp3";

    /** A dealer whose folder holds one {pose}.png per pose in ProductAssetConstants.DEALER_POSES. */
    record Dealer(UUID id, String folder, String name, String description, ProductTier tier, long priceVnd, long priceVipCoin) {
    }

    static final String DEALER_IMAGE_EXTENSION = ".png";
    static final List<Dealer> DEALERS = List.of(
            new Dealer(UUID.fromString("01a1808a-6290-78d4-b595-a218324cd012"), "dealers/ruby/", "Dealer Cổ Điển Ruby",
                    "Nữ dealer gile đen, nơ đỏ, chia bài và lật bài ngay trên bàn của bạn.", ProductTier.NORMAL, 30000, 30),
            new Dealer(UUID.fromString("01a1808a-6293-7445-a3d7-a769f2ebd497"), "dealers/neon/", "Robot Dealer Neon",
                    "Robot dealer áo tuxedo tím viền neon, mắt đổi biểu cảm theo từng ván bài.", ProductTier.VIP, 60000, 60));

    static final List<UUID> ALL_IDS = Stream.concat(Stream.of(MOCK_DECK_ID, TAUNT_SOUND_ID), DEALERS.stream().map(Dealer::id)).toList();

    static final String GRANT_KEY_PREFIX = "data-init:";
}
