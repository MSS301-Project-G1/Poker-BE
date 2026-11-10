package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.response.ProductSoundResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ProductSoundService {

    ProductSoundResponse uploadSound(UUID productId, MultipartFile file);

    ProductSoundResponse deleteSound(UUID productId);
}
