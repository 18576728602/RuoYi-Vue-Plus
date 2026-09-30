package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 情景模拟结果 VO：多情景关键指标对比 + 雷达图数据 + 瀑布图数据
 */
@Data
public class BudgetScenarioResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String planName;
    private Long orgId;
    private String orgName;

    // 雷达图维度名
    private List<String> indicators = new ArrayList<>();
    // 各情景在指标上的值（按情景分组），用于雷达图
    private List<ScenarioIndicatorSeries> series = new ArrayList<>();
    // 瀑布图：各因素对净利润的影响（乐观情景 vs 基准）
    private List<WaterfallItem> waterfall = new ArrayList<>();
    // 各情景核心指标表
    private List<ScenarioMetricRow> metrics = new ArrayList<>();

    @Data
    public static class ScenarioIndicatorSeries implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String name;
        private List<Double> values = new ArrayList<>();
    }

    @Data
    public static class WaterfallItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String name;
        private Double value;
    }

    @Data
    public static class ScenarioMetricRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String metricName;

        private List<ScenarioMetricVal> values = new ArrayList<>();
    }

    @Data
    public static class ScenarioMetricVal implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String scenarioName;
        private Double value;
    }
}