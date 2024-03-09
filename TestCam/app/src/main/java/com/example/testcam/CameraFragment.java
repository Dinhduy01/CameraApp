package com.example.testcam;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

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

public class CameraFragment extends Fragment {
    private Surface surface;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = CameraFragment.class.getSimpleName();

    private AutoFitTextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private int w, h;
    private CameraManager cameraManager;
    private String cameraId;
    private int currentCameraId;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setTextureViewAspectRatio(width, height);
            w = width;
            h = height;
            openCamera(0);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Ignored, texture size is set by the TextureView
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Invoked every time there's a new Camera preview frame
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview(flashMode);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    public static Fragment newInstance() {
        return new CameraFragment();
    }

    private View view;
    int flashMode = CaptureRequest.FLASH_MODE_TORCH; // Biến để lưu trạng thái flash hiện tại
    final int FLASH_OFF = 0;
    final int FLASH_ON = 1;
    final int FLASH_AUTO = 2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_camera, container, false);

        ImageButton flashButton = view.findViewById(R.id.flashButton);
        ImageButton captureButton = view.findViewById(R.id.captureButton);
        ImageButton changeCamera = view.findViewById(R.id.switchCameraButton);
        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Thay đổi trạng thái flash và hình ảnh tương ứng trên nút
                switch (flashMode) {
                    case FLASH_OFF:
                        flashMode = FLASH_ON;
                        flashButton.setImageResource(R.drawable.flash); // Đặt hình ảnh khi flash bật
                        // Thực hiện các hành động cần thiết khi flash bật

                        break;
                    case FLASH_ON:
                        flashMode = FLASH_AUTO;
                        flashButton.setImageResource(R.drawable.flashac); // Đặt hình ảnh khi flash tự động
                        // Thực hiện các hành động cần thiết khi flash tự động
                        break;
                    case FLASH_AUTO:
                        flashMode = FLASH_OFF;
                        flashButton.setImageResource(R.drawable.noflash); // Đặt hình ảnh khi flash tắt
                        // Thực hiện các hành động cần thiết khi flash tắt
                        break;
                }
                createCameraPreview(flashMode);
            }
        });
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        changeCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    switchCamera();
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        ImageButton galleryButton = view.findViewById(R.id.galleryButton);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
        ImageButton switchAspectRatioButton = view.findViewById(R.id.switchAspectRatioButton);
        switchAspectRatioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Xử lý sự kiện khi nhấn vào nút switchAspectRatioButton
                switchAspectRatio();
            }
        });
        return view;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("content://media/internal/images/media"));
        startActivity(intent);
    }

    private void takePicture() {
        if (cameraDevice == null) {
            Log.e(TAG, "CameraDevice is null. Cannot take picture.");
            return;
        }

        // Thực hiện các thao tác lưu ảnh vào thư mục Pictures ở đây
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.e(TAG, "Error creating media file, check storage permissions");
            return;
        }

        try {
            ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            // Bật flash khi chụp ảnh
            switch (flashMode) {
                case 0:
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    break;
                case 1:
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case 2:
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
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
                            } catch (IOException e) {
                                Log.e(TAG, "Error saving image", e);
                            }
                        }

                        image.close();
                    }

                    // Trả lại cài đặt flash về chế độ tắt (OFF)
                    createCameraPreview(CaptureRequest.FLASH_MODE_OFF);
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


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final SparseIntArray ORIENTATIONS_REAL = new SparseIntArray();

    static {
        ORIENTATIONS_REAL.append(Surface.ROTATION_0, 0);
        ORIENTATIONS_REAL.append(Surface.ROTATION_90, 90);
        ORIENTATIONS_REAL.append(Surface.ROTATION_180, 180);
        ORIENTATIONS_REAL.append(Surface.ROTATION_270, 270);
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

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        textureView = view.findViewById(R.id.surfaceView);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(1);
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

    private void stopBackgroundThread() {
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
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    return;
                }// Mặc định sử dụng camera sau
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    private void switchCamera() throws CameraAccessException {
        closeCamera();
        String[] cameraIds = cameraManager.getCameraIdList();
        // Increment or reset the camera index to switch between front and back cameras
        currentCameraId = (currentCameraId + 1) % cameraIds.length;

        // Open the new camera
        openCamera(currentCameraId);
    }

    private void createCameraPreview(int flashMode) {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // Get the supported sizes for the camera
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

            // Choose the optimal preview size based on the aspect ratio of TextureView
            Size previewSize = chooseOptimalSize(outputSizes, textureView.getWidth(), textureView.getHeight());

            // Set the default buffer size to the chosen preview size
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // Initialize surface
            surface = new Surface(texture); // Initialize surface here

            // Add surface as a target to capture request builder
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            switch (flashMode) {
                case 0:
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    break;
                case 1:
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case 2:
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
            }
            captureRequestBuilder.addTarget(surface); // Use the initialized surface here

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
        float aspectRatio = (float) textureViewWidth / textureViewHeight;
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

            ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
            layoutParams.width = newWidth;
            layoutParams.height = newHeight;
            textureView.setLayoutParams(layoutParams);
        }
    }

    public enum AspectRatio {
        SQUARE,     // 1:1
        STANDARD,   // 3:4
        WIDE        // 16:9
    }

    private AspectRatio currentAspectRatio = AspectRatio.WIDE; // Bắt đầu với tỉ lệ vuông

    private void switchAspectRatio() {
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


    private void updateTextureViewAspectRatio() {
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

            ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
            layoutParams.width = newWidth;
            layoutParams.height = newHeight;
            textureView.setLayoutParams(layoutParams);
        }
    }

    private void closeCamera() {
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
