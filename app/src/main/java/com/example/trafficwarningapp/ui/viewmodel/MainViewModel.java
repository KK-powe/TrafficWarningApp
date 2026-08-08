package com.example.trafficwarningapp.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.trafficwarningapp.data.local.WarningDatabase;
import com.example.trafficwarningapp.data.local.WarningDao;
import com.example.trafficwarningapp.data.model.AnalysisResponse;
import com.example.trafficwarningapp.data.model.TrafficStats;
import com.example.trafficwarningapp.data.model.WarningEvent;
import com.example.trafficwarningapp.data.network.ApiRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主页面ViewModel
 * 负责管理实时数据获取、本地存储和UI状态
 */
public class MainViewModel extends AndroidViewModel {

    private final ApiRepository apiRepository;
    private final WarningDao warningDao;
    private final MutableLiveData<TrafficStats> statsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<WarningEvent>> eventsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> imageUrlLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> networkStatusLiveData = new MutableLiveData<>(true);

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 是否使用模拟数据（后端未就绪时为true） */
    private boolean useMockData = true;

    /** 轮询间隔（毫秒），默认2秒 */
    private int pollingInterval = 2000;

    /** 轮询任务 */
    private Runnable pollingRunnable;

    public MainViewModel(@NonNull Application application) {
        super(application);
        apiRepository = ApiRepository.getInstance();
        warningDao = WarningDatabase.getInstance(application).warningDao();
        statsLiveData.setValue(new TrafficStats(0, 0, 0, 1));
    }

    // ==================== Getter方法 ====================

    public LiveData<TrafficStats> getStatsLiveData() {
        return statsLiveData;
    }

    public LiveData<List<WarningEvent>> getEventsLiveData() {
        return eventsLiveData;
    }

    public LiveData<String> getImageUrlLiveData() {
        return imageUrlLiveData;
    }

    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    public LiveData<Boolean> getNetworkStatusLiveData() {
        return networkStatusLiveData;
    }

    /**
     * 设置网络连接状态
     */
    public void setNetworkStatus(boolean isConnected) {
        networkStatusLiveData.setValue(isConnected);
    }

    // ==================== 数据操作 ====================

    /**
     * 开始轮询获取实时数据
     */
    public void startPolling() {
        stopPolling(); // 先停止之前的轮询
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchRealtimeData();
                pollingHandler.postDelayed(this, pollingInterval);
            }
        };
        pollingHandler.post(pollingRunnable); // 立即执行第一次
    }

    /**
     * 停止轮询
     */
    public void stopPolling() {
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    /**
     * 立即刷新一次数据
     */
    public void refreshNow() {
        fetchRealtimeData();
    }

    /**
     * 获取实时数据（在子线程执行）
     */
    private void fetchRealtimeData() {
        isLoadingLiveData.setValue(true);

        if (useMockData) {
            // 使用模拟数据
            executor.execute(() -> {
                AnalysisResponse mockResponse = apiRepository.getMockData();
                if (mockResponse.isSuccess() && mockResponse.getData() != null) {
                    // 切换到主线程更新LiveData
                    pollingHandler.post(() -> {
                        statsLiveData.setValue(mockResponse.getData().getStats());
                        eventsLiveData.setValue(mockResponse.getData().getEvents());
                        imageUrlLiveData.setValue(mockResponse.getData().getAnnotatedImageUrl());
                        isLoadingLiveData.setValue(false);
                        errorLiveData.setValue(null);
                    });
                }
            });
        } else {
            // 使用真实网络请求
            LiveData<AnalysisResponse> responseLiveData = apiRepository.getRealtimeData();
            responseLiveData.observeForever(response -> {
                if (response != null && response.isSuccess() && response.getData() != null) {
                    statsLiveData.setValue(response.getData().getStats());
                    eventsLiveData.setValue(response.getData().getEvents());
                    imageUrlLiveData.setValue(response.getData().getAnnotatedImageUrl());
                    errorLiveData.setValue(null);
                    networkStatusLiveData.setValue(true);

                    // 将事件保存到本地数据库
                    if (response.getData().getEvents() != null) {
                        executor.execute(() -> warningDao.insertAll(response.getData().getEvents()));
                    }
                } else {
                    String errorMsg = response != null ? response.getMessage() : "未知错误";
                    errorLiveData.setValue(errorMsg);
                    networkStatusLiveData.setValue(false);
                }
                isLoadingLiveData.setValue(false);
            });
        }
    }

    /**
     * 更新复核状态（本地数据库 + 提交通知服务器）
     * @param eventId 事件ID
     * @param status  复核状态：1-已确认，2-误报
     */
    public void updateReviewStatus(String eventId, int status) {
        executor.execute(() -> {
            // 先更新本地数据库
            WarningEvent event = warningDao.getEventByIdSync(eventId);
            if (event != null) {
                event.setReviewStatus(status);
                warningDao.update(event);
            }

            // 如果不是模拟数据模式，则提交到服务器
            if (!useMockData) {
                apiRepository.submitReview(eventId, status);
            }
        });
    }

    /**
     * 设置是否使用模拟数据
     */
    public void setUseMockData(boolean useMockData) {
        this.useMockData = useMockData;
    }

    /**
     * 是否正在使用模拟数据
     */
    public boolean isUseMockData() {
        return useMockData;
    }

    /**
     * 设置轮询间隔
     * @param intervalMs 间隔毫秒数
     */
    public void setPollingInterval(int intervalMs) {
        this.pollingInterval = intervalMs;
        // 重启轮询以应用新间隔
        if (pollingRunnable != null) {
            startPolling();
        }
    }

    /**
     * 获取当前轮询间隔
     */
    public int getPollingInterval() {
        return pollingInterval;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPolling();
        executor.shutdown();
    }
}
