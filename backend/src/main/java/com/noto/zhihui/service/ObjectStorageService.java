package com.noto.zhihui.service;

import java.io.InputStream;

public interface ObjectStorageService {

    boolean isAvailable();

    void upload(String storageKey, InputStream inputStream, long size, String contentType);

    InputStream download(String storageKey);

    void delete(String storageKey);
}
