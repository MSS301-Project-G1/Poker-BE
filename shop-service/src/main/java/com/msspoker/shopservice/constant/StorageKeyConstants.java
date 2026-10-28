package com.msspoker.shopservice.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Object keys the shop writes in the asset bucket. Every key sits under PUBLIC_ROOT, the one folder of the shared
 * team bucket that browsers may read (through its bucket policy, or presigned links until that policy exists).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageKeyConstants {

    public static final String PUBLIC_ROOT = "poker/shop/";
    public static final String CARD_SKINS = PUBLIC_ROOT + "skins/cards/";
    public static final String SOUNDS = PUBLIC_ROOT + "sounds/";
    public static final String UPLOADS = PUBLIC_ROOT + "uploads/";

    /** Folders under UPLOADS that the admin product form uploads to. */
    public static final String DEFAULT_UPLOAD_FOLDER = "assets";
    public static final Set<String> UPLOAD_FOLDERS = Set.of(DEFAULT_UPLOAD_FOLDER, "videos", "dealers/poses");

    /** A key is never written twice (new content gets a new key), so browsers may keep each file for a year. */
    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
}
