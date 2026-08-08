package com.example.trafficwarningapp.data.model;

/**
 * 统计信息数据类
 * 对应后端API返回的stats字段
 */
public class TrafficStats {

    /** 当前预警总数 */
    private int totalWarnings;

    /** 跟踪目标总数 */
    private int totalTracked;

    /** 高风险数量 */
    private int highRiskCount;

    /** 风险等级：1-低，2-中，3-高 */
    private int riskLevel;

    public TrafficStats() {}

    public TrafficStats(int totalWarnings, int totalTracked, int highRiskCount, int riskLevel) {
        this.totalWarnings = totalWarnings;
        this.totalTracked = totalTracked;
        this.highRiskCount = highRiskCount;
        this.riskLevel = riskLevel;
    }

    // ==================== Getters & Setters ====================

    public int getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(int totalWarnings) {
        this.totalWarnings = totalWarnings;
    }

    public int getTotalTracked() {
        return totalTracked;
    }

    public void setTotalTracked(int totalTracked) {
        this.totalTracked = totalTracked;
    }

    public int getHighRiskCount() {
        return highRiskCount;
    }

    public void setHighRiskCount(int highRiskCount) {
        this.highRiskCount = highRiskCount;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(int riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
     * 获取风险等级对应的文字描述
     */
    public String getRiskLevelText() {
        switch (riskLevel) {
            case 1:
                return "低";
            case 2:
                return "中";
            case 3:
                return "高";
            default:
                return "未知";
        }
    }
}
