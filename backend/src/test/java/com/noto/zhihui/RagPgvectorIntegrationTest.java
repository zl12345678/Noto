package com.noto.zhihui;

import com.noto.zhihui.support.AbstractIntegrationTest;
import com.noto.zhihui.support.PgVectorSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RagPgvectorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PgVectorSupport pgVectorSupport;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pgvectorExtensionIsAvailableInTestContainer() {
        assumeTrue(pgVectorSupport.isAvailable(), "pgvector image required for this test");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
        );
        assertTrue(count != null && count > 0);
    }

    @Test
    void vectorColumnExistsWhenPgvectorEnabled() {
        assumeTrue(pgVectorSupport.isAvailable(), "pgvector image required for this test");
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'note_rag_chunk'
                          AND column_name = 'embedding_vector'
                        """,
                Integer.class
        );
        assertTrue(count != null && count > 0);
    }
}
