package com.nightmarket.power.model;

/** 摊位类型：决定同时系数与基础风险分 */
public enum StallType {
    FOOD("餐饮热食", 1.00, 20),
    SNACK("小吃加工", 0.90, 14),
    DRINK("饮品冰品", 0.85, 8),
    RETAIL("百货零售", 0.60, 4),
    GAME("游艺娱乐", 0.80, 8),
    OTHER("其他", 0.80, 8);

    private final String label;
    private final double diversityFactor;
    private final int riskBase;

    StallType(String label, double diversityFactor, int riskBase) {
        this.label = label;
        this.diversityFactor = diversityFactor;
        this.riskBase = riskBase;
    }

    public String getLabel() {
        return label;
    }

    public double getDiversityFactor() {
        return diversityFactor;
    }

    public int getRiskBase() {
        return riskBase;
    }

    public static StallType fromCode(String code) {
        if (code == null) {
            return OTHER;
        }
        try {
            return StallType.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return OTHER;
        }
    }
}
