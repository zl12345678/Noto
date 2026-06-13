package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.config.NotoMinioProperties;
import com.noto.zhihui.service.ObjectStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@ConditionalOnProperty(prefix = "noto.minio", name = "enabled", havingValue = "true")
public class MinioObjectStorageServiceImpl implements ObjectStorageService {

    private final MinioClient minioClient;
    private final NotoMinioProperties properties;

    public MinioObjectStorageServiceImpl(MinioClient minioClient, NotoMinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void upload(String storageKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(storageKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.EXTERNAL_ERROR.getCode(), "文件上传失败：" + ex.getMessage());
        }
    }

    @Override
    public InputStream download(String storageKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(storageKey)
                    .build());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "文件不存在或无法读取");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(storageKey)
                    .build());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.EXTERNAL_ERROR.getCode(), "文件删除失败：" + ex.getMessage());
        }
    }
}
