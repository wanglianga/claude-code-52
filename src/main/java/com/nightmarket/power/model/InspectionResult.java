package com.nightmarket.power.model;

/** 消防抽检结果 */
public enum InspectionResult {
    PASS("检查合格"),
    RECTIFY("限期整改"),
    FAIL("不合格·立即停业");

    private final String label;

    InspectionResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static InspectionResult fromCode(String code) {
        return InspectionResult.valueOf(code.trim().toUpperCase());
    }
}
