package com.example.testcam;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ViewGallery extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private ArrayList<String> mediaList;
    private ImageView selectedImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_gallery);
        MediaController mediaController = new MediaController(this);

        ImageView imageView = findViewById(R.id.imageView);
        VideoView videoView = findViewById(R.id.videoView);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);

        mediaList = new ArrayList<>();

        getMediaFiles();
        adapter = new MediaAdapter(mediaList, this);
        recyclerView.setAdapter(adapter);
        videoView.setVisibility(View.INVISIBLE);
        imageView.setVisibility(View.INVISIBLE);
        if (mediaList.get(0).endsWith(".jpg")) {
            imageView.setVisibility(View.VISIBLE);
            Glide.with(ViewGallery.this).load(mediaList.get(0)).into(imageView);
        } else {
            videoView.setVisibility(View.VISIBLE);
            mediaController.setAnchorView(videoView);
            videoView.setVideoPath(mediaList.get(0));
            videoView.setMediaController(mediaController);
            videoView.start();
        }
        adapter.setOnItemClickListener(new MediaAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String path) {
                videoView.setVisibility(View.INVISIBLE);
                imageView.setVisibility(View.INVISIBLE);
                if (path.endsWith(".mp4")) {
                    videoView.setVisibility(View.VISIBLE);
                    mediaController.setAnchorView(videoView);
                    videoView.setVideoPath(path);
                    videoView.setMediaController(mediaController);
                    videoView.start();


                } else {
                    imageView.setVisibility(View.VISIBLE);
                    Glide.with(ViewGallery.this).load(path).into(imageView);
                }

                Toast.makeText(getApplicationContext(), path, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void getMediaFiles() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera/Image");
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                // Sắp xếp các file theo thời gian sửa đổi gần nhất
                ArrayList<File> sortedFiles = new ArrayList<>();
                Collections.addAll(sortedFiles, files);
                Collections.sort(sortedFiles, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return Long.compare(file2.lastModified(), file1.lastModified());
                    }
                });

                // Thêm đường dẫn của các file đã sắp xếp vào mediaList
                for (File file : sortedFiles) {
                    if (file.isFile()) {
                        String filePath = file.getAbsolutePath();
                        mediaList.add(filePath);
                    }
                }
            }
        }
    }

}
