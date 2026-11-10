package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.config.S3Properties;
import com.msspoker.shopservice.constant.StorageKeyConstants;
import com.msspoker.shopservice.dto.response.UploadedAssetResponse;
import com.msspoker.shopservice.exception.AppException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceImplTest {

    private static final String PUBLIC_URL = "https://team-bucket.s3.ap-southeast-1.amazonaws.com";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock
    private MinioClient minioClient;

    private MinioStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties();
        properties.setEndpoint("https://s3.ap-southeast-1.amazonaws.com");
        properties.setBucket("team-bucket");
        properties.setPublicUrl(PUBLIC_URL);
        storage = new MinioStorageServiceImpl(minioClient, properties);
    }

    @Test
    @DisplayName("Tệp admin vào poker/shop/uploads/{folder}/, trả link thường (không presign) và gắn Cache-Control")
    void uploadsAssetUnderPublicFolder() throws Exception {
        UploadedAssetResponse uploaded = storage.uploadAsset("dealers/poses",
                new MockMultipartFile("file", "Idle.PNG", "image/png", PNG));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(put.capture());
        assertThat(uploaded.key()).startsWith(StorageKeyConstants.UPLOADS + "dealers/poses/").endsWith(".png");
        assertThat(put.getValue().object()).isEqualTo(uploaded.key());
        assertThat(put.getValue().headers().get(HttpHeaders.CACHE_CONTROL)).containsExactly(StorageKeyConstants.CACHE_CONTROL);
        assertThat(uploaded.url()).isEqualTo(PUBLIC_URL + "/" + uploaded.key());
    }

    @Test
    @DisplayName("Từ chối thư mục ngoài danh sách (vd '../avatars'), không ghi gì lên bucket")
    void rejectsUnknownFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG);

        assertThatThrownBy(() -> storage.uploadAsset("../avatars", file))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_INVALID_UPLOAD_FOLDER");
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Tên tệp lạ không lái được key: bỏ đuôi không phải dạng .png")
    void dropsUnsafeExtension() {
        UploadedAssetResponse uploaded = storage.uploadAsset("assets",
                new MockMultipartFile("file", "x./../../avatars/y", "image/png", PNG));

        assertThat(uploaded.key()).startsWith(StorageKeyConstants.UPLOADS + "assets/").doesNotContain("..", "avatars");
    }

    @Test
    @DisplayName("Tệp rỗng bị từ chối")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.uploadAsset("assets", empty))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SHOP_EMPTY_UPLOAD_FILE");
    }
}
