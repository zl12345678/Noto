package com.noto.zhihui.common.util;

public record TextMatchRange(
        Integer start,
        Integer end,
        String snippet,
        String keyword
) {
}
