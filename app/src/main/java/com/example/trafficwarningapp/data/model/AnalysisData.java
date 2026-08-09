package com.example.trafficwarningapp.data.model;

import java.util.List;

/**
 * 分析数据（data字段）
 * 对应后端API返回的data字段
 */
public class AnalysisData {

    /** 统计信息 */
    private TrafficStats stats;

    /** 预警事件列表 */
    private List<WarningEvent> events;

    /** 当前标注帧图片URL */
    private String annotatedImageUrl;

    /** 服务器生成的标注结果视频URL */
    private String resultVideoUrl;

    /** 本次分析任务ID */
    private String taskId;

    public AnalysisData() {}

    public AnalysisData(TrafficStats stats, List<WarningEvent> events, String annotatedImageUrl) {
        this.stats = stats;
        this.events = events;
        this.annotatedImageUrl = annotatedImageUrl;
    }

    // ==================== Getters & Setters ====================

    public TrafficStats getStats() {
        return stats;
    }

    public void setStats(TrafficStats stats) {
        this.stats = stats;
    }

    public List<WarningEvent> getEvents() {
        return events;
    }

    public void setEvents(List<WarningEvent> events) {
        this.events = events;
    }

    public String getAnnotatedImageUrl() {
        return annotatedImageUrl;
    }

    public void setAnnotatedImageUrl(String annotatedImageUrl) {
        this.annotatedImageUrl = annotatedImageUrl;
    }

    public String getResultVideoUrl() { return resultVideoUrl; }

    public void setResultVideoUrl(String resultVideoUrl) {
        this.resultVideoUrl = resultVideoUrl;
    }

    public String getTaskId() { return taskId; }

    public void setTaskId(String taskId) { this.taskId = taskId; }
}
