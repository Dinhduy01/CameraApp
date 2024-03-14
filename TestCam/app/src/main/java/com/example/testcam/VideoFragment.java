package com.example.testcam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.testcam.databinding.FragmentVideoBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoFragment extends Fragment {
    FragmentVideoBinding binding;
    static CameraManager manager;
    private static final String TAG = VideoFragment.class.getSimpleName();
    private static AutoFitTextureView textureView;
    private static CameraDevice cameraDevice;
    private static CaptureRequest.Builder previewRequestBuilder;
    private static CameraCaptureSession cameraCaptureSession;
    private static MediaRecorder mediaRecorder;
    private HandlerThread backgroundThread;
    private static Handler backgroundHandler;
    private boolean isRecording = false;
    static boolean flashMode = false;
    static String cameraId;
    int currentCameraId = 0;

    private int w, h;
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            w = width;
            h = height;
            updateTextureViewAspectRatio();
            openCamera(currentCameraId);

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            // Ignored, Camera does not support changes in textureView size
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
    static Size size;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            size = getSize();
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    int second;
    File urlVideo;
    Handler handler = new Handler();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(getLayoutInflater());
        textureView = binding.texture;
        ImageButton captureButton = binding.video;
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureButton.setVisibility(View.INVISIBLE);
                startRecordingVideo();
                binding.recording.setVisibility(View.VISIBLE);
                binding.icon.setVisibility(View.VISIBLE);
                second = 0;
                handler.postDelayed(runnable, 1000);
                binding.switchCameraButton.setVisibility(View.INVISIBLE);
                binding.galleryButton.setVisibility(View.INVISIBLE);

            }
        });


        binding.stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.recording.setVisibility(View.INVISIBLE);
                stopRecordingVideo();
                captureButton.setVisibility(View.VISIBLE);
                binding.icon.setVisibility(View.INVISIBLE);
                handler.removeCallbacks(runnable);
                binding.switchCameraButton.setVisibility(View.VISIBLE);
                binding.galleryButton.setVisibility(View.VISIBLE);

            }
        });
        ImageButton switchCameraButton = binding.switchCameraButton;
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        ImageButton galleryButton = binding.galleryButton;
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (MainActivity.openApp) {
                    openGallery();
                } else {
                    Uri imageUri = FileProvider.getUriForFile(requireActivity(), "com.example.testcam.provider", urlVideo);
                    Log.d("Cam2", "onClick: " + imageUri);
                    Intent intent = new Intent(Intent.ACTION_VIEW);

                    intent.setDataAndType(imageUri, "video/*");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(intent);

                }

            }
        });


        return binding.getRoot();
    }

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            second++;
            String time = formatTime(second);
            binding.txtIcon.setText(time);
            handler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("DefaultLocale")
    private String formatTime(int second) {
        int hours = second / 3600;
        int min = (second % 3600) / 60;
        int sec = second % 60;
        return String.format("%02d:%02d:%02d", hours, min, sec);
    }

    private void openGallery() {
        closeCamera();
        Intent intent = new Intent(requireActivity(), ViewGallery.class);
        startActivity(intent);

    }

    private void updateTextureViewAspectRatio() {
        int width = w;
        int height = h;

        if (width > 0 && height > 0) {
            int newWidth = Math.min(width, height);
            int newHeight = newWidth;

            ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
            layoutParams.width = newWidth;
            layoutParams.height = newHeight;
            textureView.setLayoutParams(layoutParams);
        }
    }


    private void switchCamera() {
        closeCamera();
        currentCameraId = currentCameraId == 0 ? 1 : 0;
        openCamera(currentCameraId);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(0);
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
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while shutting down background thread", e);
            }
        }
    }

    private void openCamera(int currentCameraId) {
        manager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds.length > 0) {
                cameraId = cameraIds[currentCameraId];
                if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    private Size getSize() {
        StreamConfigurationMap map = null;
        try {
            map = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        assert map != null;
        Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
        return chooseOptimalSize(outputSizes, textureView.getWidth(), textureView.getHeight());
    }

    static void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        if (flashMode) {
                            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                        }
                        cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to start camera preview", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure camera preview");
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to set up camera preview", e);
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
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

    static void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        flashMode = false;
    }

    private void startRecordingVideo() {
        if (null == cameraDevice || !textureView.isAvailable()) {
            Log.e(TAG, "Camera device is not available or TextureView is not available");
            return;
        }
        try {
            setUpMediaRecorder();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            previewRequestBuilder.addTarget(previewSurface);
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            previewRequestBuilder.addTarget(recorderSurface);
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    VideoFragment.cameraCaptureSession = cameraCaptureSession;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        if (flashMode) {
                            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                        }
                        cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to start camera preview", e);
                    }
                    mediaRecorder.start();
                    isRecording = true;
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera session");
                }
            }, null);
        } catch (IOException | CameraAccessException e) {
            Log.e(TAG, "Failed to start recording", e);
        }
    }

    private void stopRecordingVideo() {
        if (!isRecording) {
            return;
        }
        isRecording = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        startPreview();
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        File newVideoFile = getOutputMediaFile();
        urlVideo = newVideoFile;
        assert newVideoFile != null;
        mediaRecorder.setOutputFile(newVideoFile.toString());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(24);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        if (currentCameraId == 0) {
            mediaRecorder.setOrientationHint(90);
        } else {
            mediaRecorder.setOrientationHint(270);
        }


        mediaRecorder.prepare();
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera/Image");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        return new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
    }
}