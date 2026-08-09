package com.example.trafficwarningapp.data.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Retrofit客户端单例
 * 统一管理网络请求的配置和实例
 */
public class RetrofitClient {

    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:6006/";
    private static RetrofitClient instance;
    private Retrofit retrofit;
    private String baseUrl;

    private RetrofitClient() {
        baseUrl = DEFAULT_BASE_URL;
        buildRetrofit();
    }

    /**
     * 获取单例实例
     */
    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    /**
     * 构建Retrofit实例
     */
    private void buildRetrofit() {
        // HTTP日志拦截器，用于调试
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 上传视频可能持续数分钟，读写超时要明显长于普通接口。
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * 获取ApiService接口实例
     */
    public ApiService getApiService() {
        return retrofit.create(ApiService.class);
    }

    /**
     * 更新服务器地址（用户从设置页修改后调用）
     * @param newBaseUrl 新的服务器地址
     */
    public void updateBaseUrl(String newBaseUrl) {
        if (newBaseUrl != null && !newBaseUrl.isEmpty()) {
            // 确保URL以"/"结尾
            if (!newBaseUrl.endsWith("/")) {
                newBaseUrl += "/";
            }
            // 确保URL以"http://"开头
            if (!newBaseUrl.startsWith("http://") && !newBaseUrl.startsWith("https://")) {
                newBaseUrl = "http://" + newBaseUrl;
            }
            this.baseUrl = newBaseUrl;
            buildRetrofit();
        }
    }

    /**
     * 获取当前服务器地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
