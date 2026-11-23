package com.supportpilot.dto;

import lombok.Data;

import java.util.List;

/**
 * 看板数据 VO，与前端 BoardData 对齐。
 */
@Data
public class BoardVO {

    private List<KpiVO> kpi;
    private List<TrendVO> trend;
    private AdoptionVO adoption;
    private List<MissVO> misses;
    /** 运营指标（SLA 达成率 / FRT / MTTR / 处理人负载 / 分类分布 / AI 收益）。 */
    private OpsVO ops;

    @Data
    public static class KpiVO {
        private String label;
        private String value;
        private String unit;
        private String delta;
        private String trend; // up | down
    }

    @Data
    public static class TrendVO {
        private String day;
        private double value;
    }

    @Data
    public static class AdoptionVO {
        private double rate;
        private List<AdoptionItemVO> items;
    }

    @Data
    public static class AdoptionItemVO {
        private String label;
        private double pct;
        private String color;
    }

    @Data
    public static class MissVO {
        private String question;
        private Integer count;
        private Integer weight;
    }

    @Data
    public static class OpsVO {
        private double slaRate;          // SLA 达成率（百分比，0-100）
        private double frtMinutes;       // 平均首次响应时长（分钟）
        private double mttrHours;        // 平均解决时长（小时）
        private double aiSavedMinutes;   // AI 节省时长估算（分钟）
        private double aiCost;           // AI 成本（元，范围内 token 用量 × 单价）
        private double kbHitRate;        // 知识库命中率 / RAG 命中率（百分比）
        private double csatAvg;          // 平均 CSAT（0-5，范围内有评分的已解决工单）
        private int csatCount;           // 范围内有 CSAT 评分的工单数
        private int resolvedCount;       // 范围内已解决工单数
        private int respondedCount;      // 范围内产生首次响应的工单数
        private List<NameCountVO> assigneeLoad; // 处理人负载 Top5
        private List<NameCountVO> categoryDist; // 分类分布
    }

    @Data
    public static class NameCountVO {
        private String name;
        private long count;
    }
}
