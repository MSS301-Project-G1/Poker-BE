package com.msspoker.shopservice.dto.response;

/**
 * A file the admin uploaded for a product.
 *
 * @param url plain link to store on the product; responses sign it while the bucket folder is private
 * @param key object key in the bucket
 */
public record UploadedAssetResponse(String url, String key) {
}
