
package com.example.testcam;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.testcam.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private FragmentManager fragmentManager;
    private String fragmentTag;
    static boolean openApp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        fragmentManager = getSupportFragmentManager();
        // Hiển thị fragment ban đầu khi activity được tạo
        displayInitialFragment();
        // Thiết lập các trình nghe sự kiện cho các nút trong layout
        setupClickListeners();
    }

    private void displayInitialFragment() {
        FragmentTransaction transaction = fragmentManager.beginTransaction(); // Bắt đầu một giao dịch fragment
        Fragment initialFragment = new CameraFragment(); // Tạo một instance của CameraFragment để hiển thị ban đầu
        fragmentTag = "camera"; // Đặt tag cho fragment
        transaction.replace(binding.fragmentContainer.getId(), initialFragment); // Thay thế fragment hiện tại bằng fragment mới
        transaction.commit(); // Kết thúc giao dịch fragment
    }

    private void setupClickListeners() {
        ImageButton switchAspectRatioButton = binding.switchAspectRatioButton; // Lấy ra nút switchAspectRatioButton từ layout
        switchAspectRatioButton.setOnClickListener(v -> switchAspectRatio()); // Thiết lập trình nghe sự kiện cho nút switchAspectRatioButton

        ImageButton changeType = binding.changeType; // Lấy ra nút changeType từ layout
        changeType.setOnClickListener(v -> {
            switchFragment(); // Gọi phương thức switchFragment khi nút được nhấn
            binding.flashButton.setChecked(false); // Đặt lại trạng thái của nút flashButton
        });

        binding.flashButton.setOnClickListener(v -> toggleFlash()); // Thiết lập trình nghe sự kiện cho nút flashButton

        binding.setting.setOnClickListener(v -> binding.settingLayout.setVisibility(View.VISIBLE)); // Hiển thị layout settingLayout khi nút setting được nhấn
        binding.closeSetting.setOnClickListener(v -> binding.settingLayout.setVisibility(View.INVISIBLE)); // Ẩn layout settingLayout khi nút closeSetting được nhấn

        binding.viewAppButton.setOnClickListener(v -> toggleAppView()); // Thiết lập trình nghe sự kiện cho nút viewAppButton
        binding.viewGalleryButton.setOnClickListener(v -> toggleAppView()); // Thiết lập trình nghe sự kiện cho nút viewGalleryButton
    }

    private void switchAspectRatio() {
        CameraFragment.switchAspectRatio(); // Gọi phương thức switchAspectRatio() của CameraFragment
    }

    private void switchFragment() {
        Fragment newFragment;
        if (fragmentTag.equals("camera")) {
            CameraFragment.closeCamera(); // Đóng camera nếu fragment hiện tại là CameraFragment
            newFragment = new VideoFragment(); // Tạo một instance của VideoFragment để hiển thị
            fragmentTag = "video"; // Đặt tag cho fragment mới
            binding.switchAspectRatioButton.setVisibility(View.INVISIBLE); // Ẩn nút switchAspectRatioButton
        } else {
            newFragment = new CameraFragment(); // Tạo một instance của CameraFragment nếu fragment hiện tại là VideoFragment
            VideoFragment.closeCamera(); // Đóng camera nếu fragment hiện tại là VideoFragment
            fragmentTag = "camera"; // Đặt tag cho fragment mới
            binding.switchAspectRatioButton.setVisibility(View.VISIBLE); // Hiển thị nút switchAspectRatioButton
        }
        Log.e(TAG, "switchFragment: "+fragmentTag );
        FragmentTransaction transaction = fragmentManager.beginTransaction(); // Bắt đầu một giao dịch fragment
        transaction.replace(binding.fragmentContainer.getId(), newFragment); // Thay thế fragment hiện tại bằng fragment mới
        transaction.addToBackStack(null); // Thêm fragment hiện tại vào stack để có thể quay lại sau này
        transaction.commit(); // Kết thúc giao dịch fragment
    }

    private void toggleFlash() {
        if (fragmentTag.equals("camera")) {
            CameraFragment.flashMode = !CameraFragment.flashMode; // Chuyển đổi trạng thái flashMode của CameraFragment
        } else {
            VideoFragment.flashMode = !VideoFragment.flashMode; // Chuyển đổi trạng thái flashMode của VideoFragment
            VideoFragment.startPreview(); // Bắt đầu xem trước video
        }
    }

    private void toggleAppView() {
        binding.viewAppButton.setChecked(!binding.viewAppButton.isChecked()); // Đảo ngược trạng thái của viewAppButton
        binding.viewGalleryButton.setChecked(!binding.viewAppButton.isChecked()); // Đảo ngược trạng thái của viewGalleryButton
        openApp = !openApp; // Chuyển đổi trạng thái hiển thị ứng dụng hoặc gallery
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // Giải phóng binding để tránh rò rỉ bộ nhớ
    }
}
