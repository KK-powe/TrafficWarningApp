package com.example.trafficwarningapp.data.model;

/**
 * API响应外层包装类
 * 对应后端API返回的完整JSON结构
 */
public class AnalysisResponse {

    /** 状态码，200表示成功 */
    private int code;

    /** 响应消息 */
    private String message;

    /** 分析数据 */
    private AnalysisData data;

    public AnalysisResponse() {}

    public AnalysisResponse(int code, String message, AnalysisData data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== Getters & Setters ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AnalysisData getData() {
        return data;
    }

    public void setData(AnalysisData data) {
        this.data = data;
    }

    /**
     * 判断响应是否成功
     */
    public boolean isSuccess() {
        return code == 200;
    }
}
