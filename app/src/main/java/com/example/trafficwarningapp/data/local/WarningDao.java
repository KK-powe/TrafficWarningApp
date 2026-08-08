package com.example.trafficwarningapp.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.trafficwarningapp.data.model.WarningEvent;

import java.util.List;

/**
 * 预警事件数据访问对象（DAO）
 * 定义数据库的增删改查操作
 */
@Dao
public interface WarningDao {

    /**
     * 插入预警事件，如果已存在则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WarningEvent event);

    /**
     * 批量插入预警事件
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<WarningEvent> events);

    /**
     * 更新预警事件（主要用于更新复核状态）
     */
    @Update
    void update(WarningEvent event);

    /**
     * 获取所有预警事件（按时间倒序）
     */
    @Query("SELECT * FROM warning_events ORDER BY timestamp DESC")
    LiveData<List<WarningEvent>> getAllEvents();

    /**
     * 根据ID获取单个预警事件
     */
    @Query("SELECT * FROM warning_events WHERE id = :eventId")
    LiveData<WarningEvent> getEventById(String eventId);

    /**
     * 根据ID获取单个预警事件（同步版本，用于非LiveData场景）
     */
    @Query("SELECT * FROM warning_events WHERE id = :eventId")
    WarningEvent getEventByIdSync(String eventId);

    /**
     * 按时间范围查询历史预警事件（倒序）
     * @param startTime 开始时间戳（毫秒）
     * @param endTime   结束时间戳（毫秒）
     */
    @Query("SELECT * FROM warning_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    LiveData<List<WarningEvent>> getEventsByTimeRange(long startTime, long endTime);

    /**
     * 删除所有预警事件
     */
    @Query("DELETE FROM warning_events")
    void deleteAll();

    /**
     * 根据ID删除预警事件
     */
    @Query("DELETE FROM warning_events WHERE id = :eventId")
    void deleteById(String eventId);

    /**
     * 获取预警事件总数
     */
    @Query("SELECT COUNT(*) FROM warning_events")
    int getEventCount();
}
