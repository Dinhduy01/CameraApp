package com.example.testcam;

import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            // Xử lý kết quả của yêu cầu quyền ở đây
            boolean allPermissionsGranted = permissions.containsValue(true);
            if (allPermissionsGranted) {
                // Nếu tất cả quyền được cấp, chuyển sang Fragment
                openVideoFragment();
            } else {
                // Nếu một hoặc nhiều quyền bị từ chối, xử lý tương ứng
                // Ví dụ: Hiển thị thông báo cần cấp quyền
            }
        });

        // Yêu cầu các quyền khi onCreate được gọi
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        // Yêu cầu quyền từ ActivityResultLauncher
        requestPermissionLauncher.launch(new String[]{
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        });
    }

    private void openVideoFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, CameraFragment.newInstance())
                .commit();
    }
}
