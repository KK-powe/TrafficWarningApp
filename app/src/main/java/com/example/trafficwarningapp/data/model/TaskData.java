package com.example.trafficwarningapp.data.model;

/** 视频分析任务状态。 */
public class TaskData {
    private String taskId;
    private String status;
    private int progress;
    private long createdAt;
    private long updatedAt;
    private String error;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
