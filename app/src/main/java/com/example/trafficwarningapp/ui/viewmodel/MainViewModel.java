package com.example.trafficwarningapp.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.trafficwarningapp.data.local.WarningDatabase;
import com.example.trafficwarningapp.data.local.WarningDao;
import com.example.trafficwarningapp.data.model.AnalysisResponse;
import com.example.trafficwarningapp.data.model.TrafficStats;
import com.example.trafficwarningapp.data.model.TaskResponse;
import com.example.trafficwarningapp.data.model.WarningEvent;
import com.example.trafficwarningapp.data.network.ApiRepository;
import com.example.trafficwarningapp.data.network.RetrofitClient;

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
    private final MutableLiveData<String> taskStatusLiveData = new MutableLiveData<>("请选择交通视频");
    private final MutableLiveData<Integer> taskProgressLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> taskRunningLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> resultVideoUrlLiveData = new MutableLiveData<>();

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 是否使用模拟数据（后端未就绪时为true） */
    private boolean useMockData = false;

    /** 轮询间隔（毫秒），默认2秒 */
    private int pollingInterval = 2000;

    /** 轮询任务 */
    private Runnable pollingRunnable;
    private Runnable taskPollingRunnable;
    private String activeTaskId;

    public MainViewModel(@NonNull Application application) {
        super(application);
        apiRepository = ApiRepository.getInstance();
        warningDao = WarningDatabase.getInstance(application).warningDao();
        statsLiveData.setValue(new TrafficStats(0, 0, 0, 1));

        SharedPreferences prefs = application.getSharedPreferences(
                "traffic_warning_settings", Context.MODE_PRIVATE);
        String serverAddress = prefs.getString(
                "server_address", RetrofitClient.getInstance().getBaseUrl());
        RetrofitClient.getInstance().updateBaseUrl(serverAddress);
        useMockData = prefs.getBoolean("use_mock_data", false);
        pollingInterval = prefs.getInt("refresh_interval", 2000);
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

    public LiveData<String> getTaskStatusLiveData() { return taskStatusLiveData; }

    public LiveData<Integer> getTaskProgressLiveData() { return taskProgressLiveData; }

    public LiveData<Boolean> getTaskRunningLiveData() { return taskRunningLiveData; }

    public LiveData<String> getResultVideoUrlLiveData() { return resultVideoUrlLiveData; }

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
            observeOnce(responseLiveData, response -> {
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

    /** 上传视频并开始轮询本次分析任务。 */
    public void uploadVideo(Uri videoUri) {
        if (videoUri == null || Boolean.TRUE.equals(taskRunningLiveData.getValue())) return;
        if (useMockData) {
            errorLiveData.setValue("请先到设置页关闭模拟数据并填写后端地址");
            return;
        }
        taskRunningLiveData.setValue(true);
        taskProgressLiveData.setValue(0);
        resultVideoUrlLiveData.setValue(null);
        taskStatusLiveData.setValue("正在上传视频…");

        LiveData<TaskResponse> upload = apiRepository.createTask(getApplication(), videoUri);
        observeOnce(upload, response -> {
            if (response != null && response.isSuccess()) {
                activeTaskId = response.getData().getTaskId();
                taskStatusLiveData.setValue("视频已上传，等待服务器分析");
                startTaskPolling();
            } else {
                taskRunningLiveData.setValue(false);
                taskStatusLiveData.setValue("上传失败");
                errorLiveData.setValue(response == null ? "上传失败" : response.getMessage());
            }
        });
    }

    private void startTaskPolling() {
        stopTaskPolling();
        taskPollingRunnable = new Runnable() {
            @Override
            public void run() {
                queryActiveTask();
                if (taskPollingRunnable != null) {
                    pollingHandler.postDelayed(this, 2000);
                }
            }
        };
        pollingHandler.post(taskPollingRunnable);
    }

    private void stopTaskPolling() {
        if (taskPollingRunnable != null) {
            pollingHandler.removeCallbacks(taskPollingRunnable);
            taskPollingRunnable = null;
        }
    }

    private void queryActiveTask() {
        if (activeTaskId == null) return;
        LiveData<TaskResponse> statusData = apiRepository.getTaskStatus(activeTaskId);
        observeOnce(statusData, response -> {
            if (response == null || !response.isSuccess()) {
                errorLiveData.setValue(response == null ? "查询任务失败" : response.getMessage());
                return;
            }
            int progress = response.getData().getProgress();
            String status = response.getData().getStatus();
            taskProgressLiveData.setValue(progress);
            if ("queued".equals(status)) {
                taskStatusLiveData.setValue("任务排队中：" + progress + "%");
            } else if ("processing".equals(status)) {
                taskStatusLiveData.setValue("YOLO正在分析：" + progress + "%");
            } else if ("completed".equals(status)) {
                stopTaskPolling();
                taskStatusLiveData.setValue("分析完成，正在获取结果…");
                fetchTaskResult(activeTaskId);
            } else if ("failed".equals(status)) {
                stopTaskPolling();
                taskRunningLiveData.setValue(false);
                taskStatusLiveData.setValue("分析失败");
                errorLiveData.setValue(response.getData().getError());
            }
        });
    }

    private void fetchTaskResult(String taskId) {
        LiveData<AnalysisResponse> resultData = apiRepository.getTaskResult(taskId);
        observeOnce(resultData, response -> {
            taskRunningLiveData.setValue(false);
            if (response != null && response.isSuccess() && response.getData() != null) {
                applyAnalysis(response);
                resultVideoUrlLiveData.setValue(response.getData().getResultVideoUrl());
                taskStatusLiveData.setValue("分析完成，可查看结果视频");
                taskProgressLiveData.setValue(100);
            } else {
                taskStatusLiveData.setValue("结果获取失败");
                errorLiveData.setValue(response == null ? "结果获取失败" : response.getMessage());
            }
        });
    }

    private void applyAnalysis(AnalysisResponse response) {
        statsLiveData.setValue(response.getData().getStats());
        eventsLiveData.setValue(response.getData().getEvents());
        imageUrlLiveData.setValue(response.getData().getAnnotatedImageUrl());
        networkStatusLiveData.setValue(true);
        errorLiveData.setValue(null);
        if (response.getData().getEvents() != null) {
            executor.execute(() -> warningDao.insertAll(response.getData().getEvents()));
        }
    }

    /** observeForever适用于仓库的一次性结果，这里收到一次后立即解除，避免轮询时累积观察者。 */
    private <T> void observeOnce(LiveData<T> source, Observer<T> observer) {
        Observer<T> wrapper = new Observer<T>() {
            @Override
            public void onChanged(T value) {
                source.removeObserver(this);
                observer.onChanged(value);
            }
        };
        source.observeForever(wrapper);
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
        stopTaskPolling();
        executor.shutdown();
    }
}
