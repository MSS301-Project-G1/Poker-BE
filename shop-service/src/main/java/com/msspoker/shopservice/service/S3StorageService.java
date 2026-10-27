package com.msspoker.shopservice.service;

import java.util.Map;

public interface S3StorageService {

    /**
     * Tải lên một tệp tin đơn lẻ lên S3.
     *
     * @param objectKey   Đường dẫn định danh file trên bucket (ví dụ: skins/cards/uuid/v1/2H.png)
     * @param content     Nội dung byte của file
     * @param contentType MIME type (ví dụ: image/png, application/json)
     * @return URL công khai của file
     */
    String uploadFile(String objectKey, byte[] content, String contentType);

    /**
     * Tải lên song song một loạt tệp tin lên S3 bằng CompletableFuture.
     *
     * @param files       Map với Key là objectKey và Value là nội dung byte
     * @param contentType MIME type mặc định
     * @return Map với Key là objectKey và Value là public URL
     */
    Map<String, String> uploadBatch(Map<String, byte[]> files, String contentType);

    /**
     * Xóa toàn bộ tệp tin trong thư mục prefix trên S3 (dùng để dọn dẹp phiên bản cũ).
     *
     * @param folderPrefix Đường dẫn tiền tố thư mục cần xóa
     */
    void deleteFolder(String folderPrefix);

    /**
     * Lấy URL công khai truy cập file từ S3.
     */
    String getPublicUrl(String objectKey);

    /**
     * Tải về nội dung byte của một tệp tin từ S3/MinIO.
     *
     * @param objectKey Đường dẫn định danh file trên bucket
     * @return Mảng byte dữ liệu file
     */
    byte[] downloadFile(String objectKey);
}
