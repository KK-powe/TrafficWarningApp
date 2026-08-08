package com.example.trafficwarningapp.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.trafficwarningapp.R;
import com.example.trafficwarningapp.ui.viewmodel.MainViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 事件详情页
 * 展示完整事件信息并提供人工复核功能
 */
public class EventDetailActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private String eventId;
    private int reviewStatus;

    // 视图引用
    private ImageView ivDetailImage;
    private TextView tvTargetId, tvType, tvClass, tvLocation, tvTime;
    private TextView tvRisk, tvStatus, tvDescription;
    private LinearLayout llReviewButtons;
    private Button btnConfirm, btnFalseAlarm;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 初始化视图
        initViews();

        // 获取Intent传递的数据
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            loadEventData(extras);
        }

        // 设置监听器
        setupListeners();
    }

    /**
     * 初始化视图引用和工具栏
     */
    private void initViews() {
        // 设置返回按钮点击事件
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_detail);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 大图
        ivDetailImage = findViewById(R.id.iv_detail_image);

        // 信息字段
        tvTargetId = findViewById(R.id.tv_detail_target_id);
        tvType = findViewById(R.id.tv_detail_type);
        tvClass = findViewById(R.id.tv_detail_class);
        tvLocation = findViewById(R.id.tv_detail_location);
        tvTime = findViewById(R.id.tv_detail_time);
        tvRisk = findViewById(R.id.tv_detail_risk);
        tvStatus = findViewById(R.id.tv_detail_status);
        tvDescription = findViewById(R.id.tv_detail_description);

        // 复核按钮区域
        llReviewButtons = findViewById(R.id.ll_review_buttons);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnFalseAlarm = findViewById(R.id.btn_false_alarm);
    }

    /**
     * 从Intent加载事件数据
     */
    private void loadEventData(Bundle extras) {
        eventId = extras.getString("event_id");
        reviewStatus = extras.getInt("event_review_status", 0);

        // 格式化时间
        long timestamp = extras.getLong("event_timestamp", 0);
        String timeStr = dateFormat.format(new Date(timestamp));

        // 设置各字段
        tvTargetId.setText(extras.getString("event_target_id", "-"));
        tvType.setText(extras.getString("event_type", getString(R.string.unknown_type)));
        tvClass.setText(extras.getString("event_target_class", "-"));
        tvLocation.setText(extras.getString("event_location", getString(R.string.unknown_location)));
        tvTime.setText(timeStr);
        tvDescription.setText(extras.getString("event_description", ""));

        // 设置风险等级（文字 + 颜色）
        int riskLevel = extras.getInt("event_risk_level", 1);
        String riskText;
        int riskColor;
        switch (riskLevel) {
            case 1:
                riskText = getString(R.string.risk_low_text);
                riskColor = getColor(R.color.risk_low);
                break;
            case 2:
                riskText = getString(R.string.risk_medium_text);
                riskColor = getColor(R.color.risk_medium);
                break;
            case 3:
                riskText = getString(R.string.risk_high_text);
                riskColor = getColor(R.color.risk_high);
                break;
            default:
                riskText = getString(R.string.risk_low_text);
                riskColor = getColor(R.color.risk_low);
                break;
        }
        tvRisk.setText(riskText);
        tvRisk.setTextColor(riskColor);

        // 设置复核状态
        updateReviewStatusUI();

        // 加载大图
        String imageUrl = extras.getString("event_image_url");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_warning)
                    .error(R.drawable.ic_warning)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(ivDetailImage);
        }

        // 如果已经复核过，隐藏复核按钮
        if (reviewStatus != 0) {
            llReviewButtons.setVisibility(View.GONE);
        }
    }

    /**
     * 设置按钮点击监听
     */
    private void setupListeners() {
        // 确认预警
        btnConfirm.setOnClickListener(v -> {
            if (eventId != null) {
                viewModel.updateReviewStatus(eventId, 1);
                reviewStatus = 1;
                updateReviewStatusUI();
                llReviewButtons.setVisibility(View.GONE);
                Toast.makeText(this, R.string.review_success, Toast.LENGTH_SHORT).show();
            }
        });

        // 误报
        btnFalseAlarm.setOnClickListener(v -> {
            if (eventId != null) {
                viewModel.updateReviewStatus(eventId, 2);
                reviewStatus = 2;
                updateReviewStatusUI();
                llReviewButtons.setVisibility(View.GONE);
                Toast.makeText(this, R.string.review_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 更新复核状态的UI显示
     */
    private void updateReviewStatusUI() {
        String statusText;
        int statusColor;
        switch (reviewStatus) {
            case 0:
                statusText = getString(R.string.status_pending);
                statusColor = getColor(R.color.text_secondary);
                break;
            case 1:
                statusText = getString(R.string.status_confirmed);
                statusColor = getColor(R.color.btn_confirm);
                break;
            case 2:
                statusText = getString(R.string.status_false_alarm);
                statusColor = getColor(R.color.btn_false_alarm);
                break;
            default:
                statusText = getString(R.string.status_pending);
                statusColor = getColor(R.color.text_secondary);
                break;
        }
        tvStatus.setText(statusText);
        tvStatus.setTextColor(statusColor);
    }
}
