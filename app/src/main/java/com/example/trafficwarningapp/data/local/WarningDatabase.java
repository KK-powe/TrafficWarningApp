package com.example.trafficwarningapp.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.trafficwarningapp.data.model.WarningEvent;

/**
 * Room数据库
 * 包含WarningEvent实体表
 */
@Database(entities = {WarningEvent.class}, version = 1, exportSchema = false)
public abstract class WarningDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "traffic_warning_db";
    private static WarningDatabase instance;

    /**
     * 获取WarningDao实例
     */
    public abstract WarningDao warningDao();

    /**
     * 获取数据库单例（线程安全）
     */
    public static synchronized WarningDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            WarningDatabase.class,
                            DATABASE_NAME)
                    .fallbackToDestructiveMigration() // 数据库版本升级时删除旧数据
                    .build();
        }
        return instance;
    }
}
