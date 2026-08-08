package com.example.trafficwarningapp.data.network;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trafficwarningapp.data.model.AnalysisData;
import com.example.trafficwarningapp.data.model.AnalysisResponse;
import com.example.trafficwarningapp.data.model.TrafficStats;
import com.example.trafficwarningapp.data.model.WarningEvent;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 数据仓库层
 * 封装网络请求，返回LiveData供ViewModel使用
 * 同时提供模拟数据方法用于调试
 */
public class ApiRepository {

    private static ApiRepository instance;
    private ApiService apiService;

    private ApiRepository() {
        apiService = RetrofitClient.getInstance().getApiService();
    }

    public static synchronized ApiRepository getInstance() {
        if (instance == null) {
            instance = new ApiRepository();
        }
        return instance;
    }

    /**
     * 获取实时分析数据
     * @return 包含AnalysisResponse的LiveData
     */
    public LiveData<AnalysisResponse> getRealtimeData() {
        MutableLiveData<AnalysisResponse> liveData = new MutableLiveData<>();

        apiService.getRealtimeData().enqueue(new Callback<AnalysisResponse>() {
            @Override
            public void onResponse(Call<AnalysisResponse> call, Response<AnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    liveData.setValue(response.body());
                } else {
                    // 请求失败，返回错误响应
                    AnalysisResponse errorResponse = new AnalysisResponse();
                    errorResponse.setCode(response.code());
                    errorResponse.setMessage("服务器响应异常");
                    liveData.setValue(errorResponse);
                }
            }

            @Override
            public void onFailure(Call<AnalysisResponse> call, Throwable t) {
                // 网络异常
                AnalysisResponse errorResponse = new AnalysisResponse();
                errorResponse.setCode(-1);
                errorResponse.setMessage("网络连接失败: " + t.getMessage());
                liveData.setValue(errorResponse);
            }
        });

        return liveData;
    }

    /**
     * 提报复核结果
     * @param eventId 事件ID
     * @param status  复核状态
     * @return 包含响应的LiveData
     */
    public LiveData<AnalysisResponse> submitReview(String eventId, int status) {
        MutableLiveData<AnalysisResponse> liveData = new MutableLiveData<>();

        apiService.submitReview(eventId, status).enqueue(new Callback<AnalysisResponse>() {
            @Override
            public void onResponse(Call<AnalysisResponse> call, Response<AnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    liveData.setValue(response.body());
                } else {
                    AnalysisResponse errorResponse = new AnalysisResponse();
                    errorResponse.setCode(response.code());
                    errorResponse.setMessage("提交复核失败");
                    liveData.setValue(errorResponse);
                }
            }

            @Override
            public void onFailure(Call<AnalysisResponse> call, Throwable t) {
                AnalysisResponse errorResponse = new AnalysisResponse();
                errorResponse.setCode(-1);
                errorResponse.setMessage("网络连接失败: " + t.getMessage());
                liveData.setValue(errorResponse);
            }
        });

        return liveData;
    }

    /**
     * 获取模拟数据（用于后端未就绪时的调试）
     * @return 模拟的AnalysisResponse
     */
    public AnalysisResponse getMockData() {
        AnalysisResponse response = new AnalysisResponse();
        response.setCode(200);
        response.setMessage("success (模拟数据)");

        AnalysisData data = new AnalysisData();

        // 模拟统计信息
        TrafficStats stats = new TrafficStats(12, 34, 3, 2);
        data.setStats(stats);

        // 模拟预警事件列表
        List<WarningEvent> events = new ArrayList<>();

        events.add(new WarningEvent(
                "evt_001",
                "疑似机动车道骑行",
                2,
                System.currentTimeMillis() - 60000,
                "http://192.168.1.100:5000/frames/frame_001.jpg",
                "电动自行车在机动车道持续行驶超过5秒，存在交通安全隐患",
                "T-012",
                "electric_bike",
                "非机动车道与机动车道交界处"
        ));

        events.add(new WarningEvent(
                "evt_002",
                "疑似逆行",
                3,
                System.currentTimeMillis() - 120000,
                "http://192.168.1.100:5000/frames/frame_002.jpg",
                "摩托车在非机动车道逆向行驶，已持续8秒",
                "T-008",
                "motorcycle",
                "解放路与中山路交叉口"
        ));

        events.add(new WarningEvent(
                "evt_003",
                "疑似违停",
                1,
                System.currentTimeMillis() - 180000,
                "http://192.168.1.100:5000/frames/frame_003.jpg",
                "私家车在公交专用道停留超过30秒",
                "T-025",
                "car",
                "公交专用道区域"
        ));

        events.add(new WarningEvent(
                "evt_004",
                "疑似占用应急车道",
                3,
                System.currentTimeMillis() - 240000,
                "http://192.168.1.100:5000/frames/frame_004.jpg",
                "货车占用应急车道行驶，车速约40km/h",
                "T-031",
                "truck",
                "高速公路应急车道"
        ));

        events.add(new WarningEvent(
                "evt_005",
                "疑似违规变道",
                2,
                System.currentTimeMillis() - 300000,
                "http://192.168.1.100:5000/frames/frame_005.jpg",
                "小轿车连续实线变道3次，影响后方车辆通行",
                "T-019",
                "car",
                "快速路主路区域"
        ));

        data.setEvents(events);
        data.setAnnotatedImageUrl("http://192.168.1.100:5000/current_frame.jpg");
        response.setData(data);

        return response;
    }
}
