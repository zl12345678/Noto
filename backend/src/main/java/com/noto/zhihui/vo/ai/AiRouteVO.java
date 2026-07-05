package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRouteVO {

    /** chat = 问答查资料；agent = 办事执行操作；clarify = 需要用户补充澄清 */
    private String intent;

    /** 简短中文说明，便于前端展示或调试 */
    private String reason;

    /** llm = 模型判断；rule = 规则兜底 */
    private String source;
}
