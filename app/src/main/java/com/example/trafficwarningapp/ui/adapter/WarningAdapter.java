package com.example.trafficwarningapp.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.trafficwarningapp.R;
import com.example.trafficwarningapp.data.model.WarningEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 预警事件列表适配器
 * 使用ListAdapter + DiffUtil优化列表更新性能
 */
public class WarningAdapter extends ListAdapter<WarningEvent, WarningAdapter.ViewHolder> {

    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /**
     * DiffUtil回调：高效对比新旧数据差异
     */
    private static final DiffUtil.ItemCallback<WarningEvent> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WarningEvent>() {
                @Override
                public boolean areItemsTheSame(@NonNull WarningEvent oldItem, @NonNull WarningEvent newItem) {
                    // 通过ID判断是否为同一项
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull WarningEvent oldItem, @NonNull WarningEvent newItem) {
                    // 判断内容是否相同（通过对比所有关键字段）
                    return oldItem.getType().equals(newItem.getType())
                            && oldItem.getRiskLevel() == newItem.getRiskLevel()
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && oldItem.getReviewStatus() == newItem.getReviewStatus();
                }
            };

    public WarningAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warning, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WarningEvent event = getItem(position);
        holder.bind(event);
    }

    /**
     * ViewHolder内部类
     */
    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumbnail;
        private final TextView tvEventType;
        private final TextView tvRiskBadge;
        private final TextView tvEventTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvEventType = itemView.findViewById(R.id.tv_event_type);
            tvRiskBadge = itemView.findViewById(R.id.tv_risk_badge);
            tvEventTime = itemView.findViewById(R.id.tv_event_time);

            // 设置点击事件
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(getItem(position));
                }
            });
        }

        /**
         * 绑定数据到视图
         */
        public void bind(WarningEvent event) {
            tvEventType.setText(event.getType());

            // 格式化时间
            tvEventTime.setText(dateFormat.format(new Date(event.getTimestamp())));

            // 设置风险等级标签
            String riskText = event.getRiskLevelText();
            tvRiskBadge.setText(riskText);
            switch (event.getRiskLevel()) {
                case 1:
                    tvRiskBadge.setBackgroundResource(R.drawable.badge_low);
                    break;
                case 2:
                    tvRiskBadge.setBackgroundResource(R.drawable.badge_medium);
                    break;
                case 3:
                    tvRiskBadge.setBackgroundResource(R.drawable.badge_high);
                    break;
            }

            // 使用Glide加载缩略图
            Glide.with(itemView.getContext())
                    .load(event.getFrameImageUrl())
                    .placeholder(R.drawable.ic_warning)
                    .error(R.drawable.ic_warning)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(ivThumbnail);
        }
    }

    /**
     * 列表项点击监听接口
     */
    public interface OnItemClickListener {
        void onItemClick(WarningEvent event);
    }
}
