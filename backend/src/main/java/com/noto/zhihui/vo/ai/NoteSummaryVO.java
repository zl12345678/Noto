package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class NoteSummaryVO {

    private String summary;
    private List<String> keyPoints;
    private List<String> riskPoints;
}
