package com.example.trafficwarningapp.data.model;

/** 创建任务和查询任务共用的响应结构。 */
public class TaskResponse {
    private int code;
    private String message;
    private TaskData data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public TaskData getData() { return data; }
    public void setData(TaskData data) { this.data = data; }
    public boolean isSuccess() { return code == 200 && data != null; }
}
