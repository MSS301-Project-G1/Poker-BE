package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.response.CardSkinUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface CardDeckSkinService {

    /**
     * Xử lý tải lên toàn bộ 53 lá bài tây từ 1 file ZIP duy nhất.
     * Tự động giải nén, kiểm tra tính đầy đủ của 53 lá, tải lên S3,
     * tự động ghép Texture Atlas Sprite Sheet và lưu Manifest JSON vào PostgreSQL.
     */
    CardSkinUploadResponse uploadDeckSkinZip(UUID productId, MultipartFile zipFile);

    /**
     * Xử lý tải lên 53 lá bài tây từ danh sách các file ảnh rời rạc.
     */
    CardSkinUploadResponse uploadDeckSkinFiles(UUID productId, List<MultipartFile> files);
}
