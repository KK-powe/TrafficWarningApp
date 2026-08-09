package com.example.trafficwarningapp.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.trafficwarningapp.R;
import com.example.trafficwarningapp.data.local.WarningDatabase;
import com.example.trafficwarningapp.data.local.WarningDao;
import com.example.trafficwarningapp.data.model.WarningEvent;
import com.example.trafficwarningapp.data.network.RetrofitClient;
import com.example.trafficwarningapp.ui.viewmodel.MainViewModel;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设置页
 * 服务器地址配置、刷新间隔、数据导出等功能
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "traffic_warning_settings";
    private static final String KEY_SERVER_ADDRESS = "server_address";
    private static final String KEY_REFRESH_INTERVAL = "refresh_interval";
    private static final String KEY_USE_MOCK_DATA = "use_mock_data";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private EditText etServerAddress;
    private Spinner spinnerInterval;
    private Button btnSaveServer, btnExport;
    private SwitchMaterial switchMockData;

    private MainViewModel viewModel;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 刷新间隔值数组（与arrays.xml中的refresh_interval_values对应）
    private final int[] intervalValues = {1000, 2000, 3000, 5000, 10000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        initViews();
        loadSettings();
        setupListeners();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        // 工具栏返回
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setNavigationOnClickListener(v -> finish());

        etServerAddress = findViewById(R.id.et_server_address);
        spinnerInterval = findViewById(R.id.spinner_interval);
        btnSaveServer = findViewById(R.id.btn_save_server);
        btnExport = findViewById(R.id.btn_export);
        switchMockData = findViewById(R.id.switch_mock_data);
    }

    /**
     * 加载已保存的设置
     */
    private void loadSettings() {
        // 加载服务器地址
        String serverAddress = prefs.getString(KEY_SERVER_ADDRESS,
                RetrofitClient.getInstance().getBaseUrl());
        etServerAddress.setText(serverAddress);

        // 加载刷新间隔
        int savedInterval = prefs.getInt(KEY_REFRESH_INTERVAL, 2000);
        int spinnerPosition = 1; // 默认2秒（索引1）
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == savedInterval) {
                spinnerPosition = i;
                break;
            }
        }
        spinnerInterval.setSelection(spinnerPosition);

        // 加载模拟数据开关
        boolean useMock = prefs.getBoolean(KEY_USE_MOCK_DATA, false);
        switchMockData.setChecked(useMock);
        viewModel.setUseMockData(useMock);
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 保存服务器地址
        btnSaveServer.setOnClickListener(v -> {
            String address = etServerAddress.getText().toString().trim();
            if (!address.isEmpty()) {
                // 更新Retrofit客户端
                RetrofitClient.getInstance().updateBaseUrl(address);
                // 保存到SharedPreferences
                prefs.edit().putString(KEY_SERVER_ADDRESS, address).apply();
                // 用户保存服务器地址就是准备联调，自动切换到真实接口。
                switchMockData.setChecked(false);
                viewModel.setUseMockData(false);
                prefs.edit().putBoolean(KEY_USE_MOCK_DATA, false).apply();
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请输入有效的服务器地址", Toast.LENGTH_SHORT).show();
            }
        });

        // 刷新间隔选择
        spinnerInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                int interval = intervalValues[position];
                viewModel.setPollingInterval(interval);
                prefs.edit().putInt(KEY_REFRESH_INTERVAL, interval).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做处理
            }
        });

        // 导出数据按钮
        btnExport.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                exportDataToCsv();
            } else {
                requestStoragePermission();
            }
        });

        // 模拟数据开关
        switchMockData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setUseMockData(isChecked);
            prefs.edit().putBoolean(KEY_USE_MOCK_DATA, isChecked).apply();
        });
    }

    /**
     * 检查存储权限
     */
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 不需要存储权限来导出
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求存储权限
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportDataToCsv();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 导出数据为CSV文件
     */
    private void exportDataToCsv() {
        executor.execute(() -> {
            try {
                WarningDao dao = WarningDatabase.getInstance(this).warningDao();
                List<WarningEvent> events = dao.getEventsByTimeRange(0, System.currentTimeMillis())
                        .getValue();

                if (events == null || events.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "暂无数据可导出", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 生成CSV内容
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String fileName = "traffic_warning_" + sdf.format(new Date()) + ".csv";

                StringBuilder csv = new StringBuilder();
                // CSV表头
                csv.append("事件ID,预警类型,风险等级,时间,目标ID,目标类别,位置,描述,复核状态\n");

                SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (WarningEvent event : events) {
                    csv.append(escapeCsv(event.getId())).append(",");
                    csv.append(escapeCsv(event.getType())).append(",");
                    csv.append(event.getRiskLevelText()).append(",");
                    csv.append(timeFormat.format(new Date(event.getTimestamp()))).append(",");
                    csv.append(escapeCsv(event.getTargetId())).append(",");
                    csv.append(escapeCsv(event.getTargetClass())).append(",");
                    csv.append(escapeCsv(event.getLocation())).append(",");
                    csv.append(escapeCsv(event.getDescription())).append(",");
                    csv.append(event.getReviewStatusText()).append("\n");
                }

                // 保存文件
                File exportDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                File file = new File(exportDir, fileName);
                FileWriter writer = new FileWriter(file);
                writer.write(csv.toString());
                writer.close();

                // 通过Intent分享文件
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/csv");
                shareIntent.putExtra(Intent.EXTRA_STREAM,
                        androidx.core.content.FileProvider.getUriForFile(
                                this,
                                getPackageName() + ".fileprovider",
                                file));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> {
                    Toast.makeText(this, "数据已导出至: " + file.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                    startActivity(Intent.createChooser(shareIntent, "分享CSV文件"));
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * CSV字段转义（处理包含逗号和引号的内容）
     */
    private String escapeCsv(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
