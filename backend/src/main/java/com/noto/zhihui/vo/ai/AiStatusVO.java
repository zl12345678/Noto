package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiStatusVO {

    private boolean enabled;
    private boolean configured;
    private String model;
    private String provider;
}
