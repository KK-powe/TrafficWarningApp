package com.example.trafficwarningapp.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.trafficwarningapp.R;
import com.example.trafficwarningapp.data.model.TrafficStats;
import com.example.trafficwarningapp.data.model.WarningEvent;
import com.example.trafficwarningapp.ui.adapter.WarningAdapter;
import com.example.trafficwarningapp.ui.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

/**
 * 主页面（实时预警）
 * 负责展示实时数据、统计卡片和预警事件列表
 */
public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private WarningAdapter adapter;

    // 视图引用
    private TextView tvRiskLevel;
    private View dotRiskLevel;
    private TextView tvTotalWarnings, tvTotalTracked, tvHighRisk;
    private RecyclerView rvWarningEvents;
    private TextView tvNoData;
    private TextView tvTaskStatus;
    private ProgressBar progressTask;
    private View btnSelectVideo, btnOpenResult;
    private String resultVideoUrl;

    private final ActivityResultLauncher<String> videoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    viewModel.uploadVideo(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 初始化视图
        initViews();

        // 设置底部导航栏
        setupBottomNav();

        // 设置RecyclerView
        setupRecyclerView();

        // 观察数据变化
        observeData();

        // 检查网络状态
        checkNetworkStatus();
    }

    /**
     * 初始化所有视图引用和事件
     */
    private void initViews() {
        // 风险等级指示器
        tvRiskLevel = findViewById(R.id.tv_risk_level);
        dotRiskLevel = findViewById(R.id.dot_risk_level);

        // 统计卡片
        tvTotalWarnings = findViewById(R.id.tv_total_warnings);
        tvTotalTracked = findViewById(R.id.tv_total_tracked);
        tvHighRisk = findViewById(R.id.tv_high_risk);

        // 列表
        rvWarningEvents = findViewById(R.id.rv_warning_events);
        tvNoData = findViewById(R.id.tv_no_data);

        tvTaskStatus = findViewById(R.id.tv_task_status);
        progressTask = findViewById(R.id.progress_task);
        btnSelectVideo = findViewById(R.id.btn_select_video);
        btnOpenResult = findViewById(R.id.btn_open_result);
        btnSelectVideo.setOnClickListener(v -> videoPicker.launch("video/*"));
        btnOpenResult.setOnClickListener(v -> openResultVideo());

        // 刷新按钮
        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
            viewModel.refreshNow();
        });
    }

    /**
     * 设置底部导航栏
     */
    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        // 默认选中"实时"
        bottomNav.setSelectedItemId(R.id.nav_realtime);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
                return false; // 不改变选中状态（因为跳转到了新页面）
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return false;
            }
            // nav_realtime，保持在当前页面
            return true;
        });
    }

    /**
     * 初始化RecyclerView和Adapter
     */
    private void setupRecyclerView() {
        rvWarningEvents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WarningAdapter(event -> {
            // 点击事件：跳转到事件详情页
            Intent intent = new Intent(MainActivity.this, EventDetailActivity.class);
            intent.putExtra("event_id", event.getId());
            intent.putExtra("event_type", event.getType());
            intent.putExtra("event_risk_level", event.getRiskLevel());
            intent.putExtra("event_timestamp", event.getTimestamp());
            intent.putExtra("event_image_url", event.getFrameImageUrl());
            intent.putExtra("event_description", event.getDescription());
            intent.putExtra("event_target_id", event.getTargetId());
            intent.putExtra("event_target_class", event.getTargetClass());
            intent.putExtra("event_location", event.getLocation());
            intent.putExtra("event_review_status", event.getReviewStatus());
            startActivity(intent);
        });
        rvWarningEvents.setAdapter(adapter);
    }

    /**
     * 观察ViewModel的LiveData变化并更新UI
     */
    private void observeData() {
        // 观察统计信息
        viewModel.getStatsLiveData().observe(this, this::updateStats);

        // 观察预警事件列表
        viewModel.getEventsLiveData().observe(this, events -> {
            if (events != null && !events.isEmpty()) {
                adapter.submitList(events);
                rvWarningEvents.setVisibility(View.VISIBLE);
                tvNoData.setVisibility(View.GONE);
            } else {
                rvWarningEvents.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
            }
        });

        // 观察当前帧图片URL
        viewModel.getImageUrlLiveData().observe(this, url -> {
            if (url != null && !url.isEmpty()) {
                findViewById(R.id.tv_image_placeholder).setVisibility(View.GONE);
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.ic_warning)
                        .error(R.drawable.ic_warning)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .centerCrop()
                        .into((ImageView) findViewById(R.id.iv_frame_image));
            }
        });

        // 观察加载状态
        viewModel.getIsLoadingLiveData().observe(this, isLoading -> {
            findViewById(R.id.progress_image).setVisibility(
                    Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
        });

        // 观察网络状态
        viewModel.getNetworkStatusLiveData().observe(this, isConnected -> {
            if (Boolean.FALSE.equals(isConnected)) {
                Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show();
            }
        });

        // 观察错误信息
        viewModel.getErrorLiveData().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getTaskStatusLiveData().observe(this, tvTaskStatus::setText);
        viewModel.getTaskProgressLiveData().observe(this, progress ->
                progressTask.setProgress(progress == null ? 0 : progress));
        viewModel.getTaskRunningLiveData().observe(this, running -> {
            boolean isRunning = Boolean.TRUE.equals(running);
            progressTask.setVisibility(isRunning ? View.VISIBLE : View.GONE);
            btnSelectVideo.setEnabled(!isRunning);
        });
        viewModel.getResultVideoUrlLiveData().observe(this, url -> {
            resultVideoUrl = url;
            btnOpenResult.setVisibility(
                    url == null || url.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void openResultVideo() {
        if (resultVideoUrl == null || resultVideoUrl.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(resultVideoUrl));
        intent.setDataAndType(Uri.parse(resultVideoUrl), "video/*");
        try {
            startActivity(intent);
        } catch (Exception exception) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(resultVideoUrl)));
        }
    }

    /**
     * 更新统计卡片数据
     */
    private void updateStats(TrafficStats stats) {
        if (stats == null) return;

        tvTotalWarnings.setText(String.valueOf(stats.getTotalWarnings()));
        tvTotalTracked.setText(String.valueOf(stats.getTotalTracked()));
        tvHighRisk.setText(String.valueOf(stats.getHighRiskCount()));

        // 更新风险等级指示器
        int riskLevel = stats.getRiskLevel();
        int colorRes;
        int dotRes;
        String riskText;

        switch (riskLevel) {
            case 1:
                colorRes = getColor(R.color.risk_low);
                dotRes = R.drawable.badge_low;
                riskText = getString(R.string.risk_low_text);
                break;
            case 2:
                colorRes = getColor(R.color.risk_medium);
                dotRes = R.drawable.badge_medium;
                riskText = getString(R.string.risk_medium_text);
                break;
            case 3:
                colorRes = getColor(R.color.risk_high);
                dotRes = R.drawable.badge_high;
                riskText = getString(R.string.risk_high_text);
                break;
            default:
                colorRes = getColor(R.color.risk_low);
                dotRes = R.drawable.badge_low;
                riskText = getString(R.string.risk_low_text);
                break;
        }

        ((GradientDrawable) dotRiskLevel.getBackground()).setColor(colorRes);
        tvRiskLevel.setText(riskText);
        tvRiskLevel.setTextColor(colorRes);
    }

    /**
     * 检查网络连接状态
     */
    private void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        viewModel.setNetworkStatus(isConnected);

        if (!isConnected) {
            Toast.makeText(this, R.string.network_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复时启动数据轮询
        viewModel.startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面不可见时停止轮询，节省资源
        viewModel.stopPolling();
    }
}
