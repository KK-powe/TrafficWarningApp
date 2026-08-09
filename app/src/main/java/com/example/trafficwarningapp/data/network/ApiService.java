package com.example.trafficwarningapp.data.network;

import com.example.trafficwarningapp.data.model.AnalysisResponse;
import com.example.trafficwarningapp.data.model.TaskResponse;

import okhttp3.MultipartBody;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

/**
 * Retrofit API接口定义
 * 定义所有与后端通信的HTTP方法
 */
public interface ApiService {

    /** 上传视频并创建后台分析任务。 */
    @Multipart
    @POST("api/tasks")
    Call<TaskResponse> createTask(@Part MultipartBody.Part video);

    /** 查询任务处理状态和进度。 */
    @GET("api/tasks/{taskId}")
    Call<TaskResponse> getTaskStatus(@Path("taskId") String taskId);

    /** 获取已完成任务的完整识别结果。 */
    @GET("api/tasks/{taskId}/result")
    Call<AnalysisResponse> getTaskResult(@Path("taskId") String taskId);

    /**
     * 获取实时分析数据（包含统计信息、预警事件列表、当前帧图片）
     */
    @GET("api/analysis/realtime")
    Call<AnalysisResponse> getRealtimeData();

    /**
     * 获取历史分析数据（按日期范围查询）
     */
    @GET("api/analysis/history")
    Call<AnalysisResponse> getHistoryData();

    /**
     * 提报复核结果到服务器
     * @param eventId  事件ID
     * @param status   复核状态：1-已确认，2-误报
     */
    @POST("api/review/{eventId}/{status}")
    Call<AnalysisResponse> submitReview(@Path("eventId") String eventId,
                                        @Path("status") int status);
}
