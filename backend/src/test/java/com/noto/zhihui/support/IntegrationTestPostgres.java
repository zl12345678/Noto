package com.noto.zhihui.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 全套件共享一个 PostgreSQL 容器，避免多测试类重复启动导致 Spring 上下文指向已关闭的连接。
 */
public final class IntegrationTestPostgres {

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("noto_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema-init.sql");

    static {
        CONTAINER.start();
    }

    private IntegrationTestPostgres() {
    }

    public static PostgreSQLContainer<?> container() {
        return CONTAINER;
    }
}
