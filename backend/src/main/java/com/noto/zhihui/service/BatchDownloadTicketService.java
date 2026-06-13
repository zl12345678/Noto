package com.noto.zhihui.service;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BatchDownloadTicketService {

    private static final Logger log = LoggerFactory.getLogger(BatchDownloadTicketService.class);
    private static final long TTL_MINUTES = 10;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String create(Path zipFile, String filename, Long userId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Ticket(zipFile, filename, userId, Instant.now().plusSeconds(TTL_MINUTES * 60)));
        return ticket;
    }

    public Ticket consume(String ticket, Long optionalUserId) {
        Ticket entry = tickets.remove(ticket);
        if (entry == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "下载链接无效或已使用");
        }
        if (optionalUserId != null && !entry.userId.equals(optionalUserId)) {
            safeDelete(entry.path);
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            safeDelete(entry.path);
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "下载链接已过期，请重新发起批量下载");
        }
        if (!Files.exists(entry.path)) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "下载文件不存在，请重新发起批量下载");
        }
        return entry;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, Ticket> entry : tickets.entrySet()) {
            if (now.isAfter(entry.getValue().expiresAt)) {
                Ticket removed = tickets.remove(entry.getKey());
                if (removed != null) {
                    safeDelete(removed.path);
                }
            }
        }
    }

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete expired batch download file {}", path, ex);
        }
    }

    public record Ticket(Path path, String filename, Long userId, Instant expiresAt) {
    }
}
