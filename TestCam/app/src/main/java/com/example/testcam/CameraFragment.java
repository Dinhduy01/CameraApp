package com.example.testcam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.testcam.databinding.FragmentCameraBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CameraFragment extends Fragment {

    private FragmentCameraBinding binding;

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = CameraFragment.class.getSimpleName();

    private static AutoFitTextureView textureView;
    private static CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private static CameraCaptureSession cameraCaptureSession;
    private static int w;
    private static int h;
    private CameraManager cameraManager;
    private String cameraId;
    private int currentCameraId;
    private static Handler backgroundHandler;
    private static HandlerThread backgroundThread;

    static boolean flashMode = false;

    File urlImg;
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            setTextureViewAspectRatio(width, height);
            w = width;
            h = height;
            openCamera(0);

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            // Invoked every time there's a new Camera preview frame
        }
    };

    //lắng nghe các sự kiện liên quan đến trạng thái của CameraDevice
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
        }
    };

    public static Fragment newInstance() {
        return new CameraFragment();
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCameraBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(Key.CAM_ID, currentCameraId);
    }

    private void takePicture() {
        if (cameraDevice == null) {
            Log.e(TAG, "CameraDevice is null. Cannot take picture.");
            return;
        }

        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.e(TAG, "Error creating media file, check storage permissions");
            return;
        }

        try {
            ImageReader reader = ImageReader.newInstance(textureView.getWidth(), textureView.getHeight(), ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            if (currentCameraId == 0) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            } else {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270);
            }
            // Bật flash khi chụp ảnh
            if (flashMode) {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(getActivity(), "Image Captured!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Image Captured!");
                    // Add code to handle captured image
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        File pictureFile = getOutputMediaFile();
                        if (pictureFile != null) {
                            try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                                fos.write(bytes);
                                Log.d(TAG, "Image saved: " + pictureFile.getAbsolutePath());
                                urlImg = pictureFile;
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Glide.with(requireContext()).load(pictureFile).into(binding.galleryButton);
                                    }
                                });
                            } catch (IOException e) {
                                Log.e(TAG, "Error saving image", e);

                            }
                        }

                        image.close();
                    }
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Cannot access the camera", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure camera");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }


    // Phương thức để tạo tập tin lưu ảnh trong thư mục Pictures
    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera/Image");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }

    ArrayList<File> mediaList = new ArrayList<>();

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        textureView = binding.surfaceView;
        getMediaFiles();
        if (!mediaList.isEmpty()) {
            urlImg = mediaList.get(0);
            Glide.with(requireActivity()).load(mediaList.get(0)).into(binding.galleryButton);
        }


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
                    if (file.isFile() && file.getName().endsWith(".jpg")) {
                        mediaList.add(file);
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(currentCameraId);
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    static void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while shutting down background thread", e);
        }
    }

    private void openCamera(int currentCameraId) {
        cameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length > 0) {
                cameraId = cameraIds[currentCameraId];
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION);
                    return;
                }// Mặc định sử dụng camera sau

                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    private void switchCamera() {
        closeCamera();
        currentCameraId = currentCameraId == 0 ? 1 : 0;
        openCamera(currentCameraId);
    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // Get the supported sizes for the camera
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

            // Choose the optimal preview size based on the aspect ratio of TextureView
            Size previewSize = chooseOptimalSize(outputSizes, textureView.getWidth(), textureView.getHeight());

            // Set the default buffer size to the chosen preview size
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Cannot access the camera", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    // Comparator for comparing sizes by their areas
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private void setTextureViewAspectRatio(int width, int height) {
        if (width > 0 && height > 0) {
            int newWidth;
            int newHeight;

            newWidth = width;
            newHeight = (int) (width * 1.33); // 3:4 = 4:3/3

            textureView.setAspectRatio(newWidth, newHeight);
        }
    }

    public enum AspectRatio {
        SQUARE,     // 1:1
        STANDARD,   // 3:4
        WIDE        // 16:9
    }

    private static AspectRatio currentAspectRatio = AspectRatio.WIDE; // Bắt đầu với tỉ lệ vuông

    static void switchAspectRatio() {
        switch (currentAspectRatio) {
            case SQUARE:
                currentAspectRatio = AspectRatio.STANDARD;
                break;
            case STANDARD:
                currentAspectRatio = AspectRatio.WIDE;
                break;
            case WIDE:
                currentAspectRatio = AspectRatio.SQUARE;
                break;
        }

        // Cập nhật giao diện người dùng
        updateTextureViewAspectRatio();
    }


    private static void updateTextureViewAspectRatio() {
        int width = w;
        int height = h;

        if (width > 0 && height > 0) {
            int newWidth;
            int newHeight;

            switch (currentAspectRatio) {
                case SQUARE:
                    newWidth = Math.min(width, height);
                    newHeight = newWidth;
                    break;
                case STANDARD:
                    newWidth = width;
                    newHeight = (int) (width * 1.33); // 3:4 = 4:3/3
                    break;
                case WIDE:
                    newWidth = width;
                    newHeight = (int) (width * 9 / 16); // 16:9
                    break;
                default:
                    newWidth = width;
                    newHeight = height;
                    break;
            }

            textureView.setAspectRatio(newWidth, newHeight);
        }
    }

    static void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
}
