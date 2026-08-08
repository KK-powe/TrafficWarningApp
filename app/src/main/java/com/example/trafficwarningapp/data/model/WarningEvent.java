package com.example.trafficwarningapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 预警事件数据类
 * 对应后端API返回的events数组中的每一项
 * 同时作为Room数据库的实体表
 */
@Entity(tableName = "warning_events")
public class WarningEvent {

    @PrimaryKey
    @NonNull
    private String id;

    /** 预警类型，如"疑似机动车道骑行" */
    private String type;

    /** 风险等级：1-低，2-中，3-高 */
    private int riskLevel;

    /** 事件时间戳（毫秒） */
    private long timestamp;

    /** 带标注框的帧图片URL */
    private String frameImageUrl;

    /** 预警描述 */
    private String description;

    /** 跟踪目标ID */
    private String targetId;

    /** 目标类别，如"electric_bike" */
    private String targetClass;

    /** 位置描述 */
    private String location;

    /** 复核状态：0-待复核，1-已确认，2-误报 */
    private int reviewStatus;

    /**
     * Room需要无参构造函数
     */
    public WarningEvent() {}

    @Ignore
    public WarningEvent(String id, String type, int riskLevel, long timestamp,
                        String frameImageUrl, String description, String targetId,
                        String targetClass, String location) {
        this.id = id;
        this.type = type;
        this.riskLevel = riskLevel;
        this.timestamp = timestamp;
        this.frameImageUrl = frameImageUrl;
        this.description = description;
        this.targetId = targetId;
        this.targetClass = targetClass;
        this.location = location;
        this.reviewStatus = 0; // 默认待复核
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFrameImageUrl() {
        return frameImageUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public String getLocation() {
        return location;
    }

    public int getReviewStatus() {
        return reviewStatus;
    }

    // ==================== Setters ====================

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRiskLevel(int riskLevel) {
        this.riskLevel = riskLevel;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setFrameImageUrl(String frameImageUrl) {
        this.frameImageUrl = frameImageUrl;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setReviewStatus(int reviewStatus) {
        this.reviewStatus = reviewStatus;
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

    /**
     * 获取复核状态的文字描述
     */
    public String getReviewStatusText() {
        switch (reviewStatus) {
            case 0:
                return "待复核";
            case 1:
                return "已确认";
            case 2:
                return "误报";
            default:
                return "未知";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WarningEvent that = (WarningEvent) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
