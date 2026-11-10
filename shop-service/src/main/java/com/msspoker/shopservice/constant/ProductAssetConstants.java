package com.msspoker.shopservice.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Category codes and metadata keys that game clients read from a product.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductAssetConstants {

    public static final String CARD_SKIN_CATEGORY = "CARD_SKIN";
    public static final String SOUND_CATEGORY = "SOUND";
    public static final String DEALER_CATEGORY = "DEALER";

    /** Metadata field written by ProductSoundServiceImpl when an admin attaches a sound file. */
    public static final String SOUND_URL_KEY = "soundUrl";

    /** Metadata object of a dealer: one image per pose, as the admin dealer form writes it. */
    public static final String DEALER_POSES_KEY = "poses";
    public static final String DEALER_AVATAR_POSE = "avatar";
    public static final List<String> DEALER_POSES = List.of("idle", "deal", "flip", "win", DEALER_AVATAR_POSE);
}
