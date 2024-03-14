package com.example.testcam;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {

    private ArrayList<String> mediaList;
    private Context context;
    private OnItemClickListener listener;

    MediaAdapter(ArrayList<String> mediaList, Context context) {
        this.mediaList = mediaList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String mediaPath = mediaList.get(position);
        File file = new File(mediaPath);

        if (file.exists()) {
            // Đối với hình ảnh
            if (mediaPath.endsWith(".mp4")) {
                // Nếu là video, sử dụng Glide để tạo thumbnail từ video
                Glide.with(context)
                        .asBitmap()
                        .load(Uri.fromFile(new File(mediaPath)))
                        .thumbnail(0.1f) // Tăng thumbnail để tải nhanh hơn
                        .centerCrop()
                        .into(holder.imageView);
            } else {
                // Nếu là ảnh, sử dụng Glide để tải ảnh từ file ảnh
                Glide.with(context)
                        .load(Uri.fromFile(new File(mediaPath)))
                        .placeholder(R.drawable.pic) // Ảnh mặc định trong khi tải
                        .error(R.drawable.pic) // Ảnh mặc định nếu có lỗi
                        .centerCrop()
                        .into(holder.imageView);
            }
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClick(mediaPath);
                    }
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(String path);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}