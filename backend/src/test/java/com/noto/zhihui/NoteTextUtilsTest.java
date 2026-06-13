package com.noto.zhihui;

import com.noto.zhihui.common.util.NoteTextUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteTextUtilsTest {

    @Test
    void highlightHtmlShouldEscapeScriptTags() {
        String content = "<img src=x onerror=alert(1)> keyword here";
        String html = NoteTextUtils.highlightHtml(content, "keyword");
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;img"));
        assertTrue(html.contains("<em>keyword</em>"));
    }
}
