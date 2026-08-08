package com.example.trafficwarningapp.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trafficwarningapp.R;
import com.example.trafficwarningapp.data.local.WarningDatabase;
import com.example.trafficwarningapp.data.local.WarningDao;
import com.example.trafficwarningapp.data.model.WarningEvent;
import com.example.trafficwarningapp.ui.viewmodel.MainViewModel;
import com.example.trafficwarningapp.ui.adapter.WarningAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 历史记录页
 * 按日期范围查询本地数据库中的历史预警事件
 */
public class HistoryActivity extends AppCompatActivity {

    private WarningDao warningDao;
    private MainViewModel viewModel;
    private WarningAdapter adapter;
    private RecyclerView rvHistory;
    private TextView tvNoHistory;
    private Button btnStartDate, btnEndDate, btnQuery;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar endCalendar = Calendar.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private long startTimeMs = 0;
    private long endTimeMs = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 初始化数据库
        warningDao = WarningDatabase.getInstance(this).warningDao();
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        initViews();
        setupListeners();

        // 设置默认日期范围（最近7天）
        endCalendar.setTimeInMillis(System.currentTimeMillis());
        startCalendar.setTimeInMillis(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L);
        startTimeMs = startCalendar.getTimeInMillis();
        endTimeMs = endCalendar.getTimeInMillis();

        // 更新日期按钮显示
        updateDateButtonText();

        // 设置RecyclerView
        setupRecyclerView();

        // 查询历史数据
        queryHistory();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        // 工具栏返回
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_history);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvHistory = findViewById(R.id.rv_history);
        tvNoHistory = findViewById(R.id.tv_no_history);
        btnStartDate = findViewById(R.id.btn_start_date);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnQuery = findViewById(R.id.btn_query);
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 开始日期选择
        btnStartDate.setOnClickListener(v -> showDatePicker(startCalendar, date -> {
            startTimeMs = date;
            btnStartDate.setText(displayFormat.format(new Date(date)));
        }));

        // 结束日期选择
        btnEndDate.setOnClickListener(v -> showDatePicker(endCalendar, date -> {
            endTimeMs = date;
            btnEndDate.setText(displayFormat.format(new Date(date)));
        }));

        // 查询按钮
        btnQuery.setOnClickListener(v -> queryHistory());
    }

    /**
     * 显示日期选择对话框
     */
    private void showDatePicker(Calendar calendar, OnDateSelectedListener listener) {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, dayOfMonth, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    listener.onDateSelected(cal.getTimeInMillis());
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    /**
     * 更新日期按钮的文字显示
     */
    private void updateDateButtonText() {
        btnStartDate.setText(displayFormat.format(new Date(startTimeMs)));
        btnEndDate.setText(displayFormat.format(new Date(endTimeMs)));
    }

    /**
     * 初始化RecyclerView
     */
    private void setupRecyclerView() {
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WarningAdapter(event -> {
            // 点击跳转到事件详情
            Intent intent = new Intent(HistoryActivity.this, EventDetailActivity.class);
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
        rvHistory.setAdapter(adapter);
    }

    /**
     * 从数据库查询历史数据
     */
    private void queryHistory() {
        // 将结束日期设为当天的23:59:59
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(endTimeMs);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        long adjustedEndTime = endCal.getTimeInMillis();

        // 在子线程执行数据库查询
        executor.execute(() -> {
            WarningDao dao = WarningDatabase.getInstance(this).warningDao();

            // 使用LiveData观察数据变化
            runOnUiThread(() -> {
                dao.getEventsByTimeRange(startTimeMs, adjustedEndTime)
                        .observe(this, events -> {
                            if (events != null && !events.isEmpty()) {
                                adapter.submitList(events);
                                rvHistory.setVisibility(View.VISIBLE);
                                tvNoHistory.setVisibility(View.GONE);
                            } else {
                                rvHistory.setVisibility(View.GONE);
                                tvNoHistory.setVisibility(View.VISIBLE);
                            }
                        });
            });
        });
    }

    /**
     * 日期选择回调接口
     */
    private interface OnDateSelectedListener {
        void onDateSelected(long timestamp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
