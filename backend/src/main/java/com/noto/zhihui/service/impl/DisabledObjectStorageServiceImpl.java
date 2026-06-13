package com.noto.zhihui.service.impl;

import com.noto.zhihui.service.ObjectStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@ConditionalOnProperty(prefix = "noto.minio", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledObjectStorageServiceImpl implements ObjectStorageService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void upload(String storageKey, InputStream inputStream, long size, String contentType) {
    }

    @Override
    public InputStream download(String storageKey) {
        return InputStream.nullInputStream();
    }

    @Override
    public void delete(String storageKey) {
    }
}
