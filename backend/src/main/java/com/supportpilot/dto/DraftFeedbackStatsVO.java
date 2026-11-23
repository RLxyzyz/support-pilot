package com.supportpilot.dto;

import lombok.Data;

import java.util.List;

/** 草稿反馈统计：采纳 / 驳回 / 被编辑数量 + 驳回原因分布。 */
@Data
public class DraftFeedbackStatsVO {
    private long adopted;
    private long rejected;
    private long edited;
    private List<ReasonVO> reasons;

    @Data
    public static class ReasonVO {
        private String reason;
        private long count;
    }
}
