package com.duy.nguyen.ardemo;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.duy.nguyen.ardemo.helpers.CameraPermissionHelper;
import com.duy.nguyen.ardemo.helpers.DisplayRotationHelper;
import com.duy.nguyen.ardemo.helpers.FullScreenHelper;
import com.duy.nguyen.ardemo.helpers.TapHelper;
import com.duy.nguyen.ardemo.helpers.TrackingStateHelper;
import com.duy.nguyen.ardemo.rendering.BackgroundRenderer;
import com.duy.nguyen.ardemo.rendering.ObjectRenderer;
import com.duy.nguyen.ardemo.rendering.ObjectRenderer.BlendMode;
import com.duy.nguyen.ardemo.rendering.PlaneRenderer;
import com.duy.nguyen.ardemo.rendering.PointCloudRenderer;
import com.duy.nguyen.ardemo.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MainActivity extends AppCompatActivity
        implements GLSurfaceView.Renderer,
        ImageReader.OnImageAvailableListener,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private boolean arMode = false;
    private final AtomicBoolean isFirstFrameWithoutArcore = new AtomicBoolean(true);
    private GLSurfaceView surfaceView;
    private Session sharedSession;
    private CameraCaptureSession captureSession;
    private CameraManager cameraManager;
    private List<CaptureRequest.Key<?>> keysThatCanCauseCaptureDelaysWhenModified;
    private CameraDevice cameraDevice;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private SharedCamera sharedCamera;
    private String cameraId;
    private final AtomicBoolean shouldUpdateSurfaceTexture = new AtomicBoolean(false);
    private boolean arcoreActive;
    private boolean surfaceCreated;
    private boolean errorCreatingSession = false;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private ImageReader cpuImageReader;
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};
    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);
    private boolean captureSessionChangesPossible = true;
    private final ConditionVariable safeToExitApp = new ConditionVariable();

    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final CameraDevice.StateCallback cameraDeviceCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " opened.");
                    MainActivity.this.cameraDevice = cameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onClosed(@NonNull CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " closed.");
                    MainActivity.this.cameraDevice = null;
                    safeToExitApp.open();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.w(TAG, "Camera device ID " + cameraDevice.getId() + " disconnected.");
                    cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    Log.e(TAG, "Camera device ID " + cameraDevice.getId() + " error " + error);
                    cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                    finish();
                }
            };

    CameraCaptureSession.StateCallback cameraSessionStateCallback =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                  Log.d(TAG, "Camera capture session configured.");
                  captureSession = session;
                  setRepeatingCaptureRequest();
                }

                @Override
                public void onSurfacePrepared(
                        @NonNull CameraCaptureSession session, @NonNull Surface surface) {
                    Log.d(TAG, "Camera capture surface prepared.");
                }

                @Override
                public void onReady(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session ready.");
                }

                @Override
                public void onActive(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session active.");
                    if (!arcoreActive) {
                        resumeARCore();
                    }
                    synchronized (MainActivity.this) {
                        captureSessionChangesPossible = true;
                        MainActivity.this.notify();
                    }
                }

                @Override
                public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
                    Log.w(TAG, "Camera capture queue empty.");
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "Camera capture session closed.");
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure camera capture session.");
                }
            };

    private final CameraCaptureSession.CaptureCallback cameraCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                    shouldUpdateSurfaceTexture.set(true);
                }

                @Override
                public void onCaptureBufferLost(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull Surface target,
                        long frameNumber) {
                    Log.e(TAG, "onCaptureBufferLost: " + frameNumber);
                }

                @Override
                public void onCaptureFailed(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureFailure failure) {
                    Log.e(TAG, "onCaptureFailed: " + failure.getFrameNumber() + " " + failure.getReason());
                }

                @Override
                public void onCaptureSequenceAborted(
                        @NonNull CameraCaptureSession session, int sequenceId) {
                    Log.e(TAG, "onCaptureSequenceAborted: " + sequenceId + " " + session);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      Bundle extraBundle = getIntent().getExtras();
      if (extraBundle != null && 1 == extraBundle.getShort(AUTOMATOR_KEY, AUTOMATOR_DEFAULT)) {
        automatorRun.set(true);
      }
      surfaceView = findViewById(R.id.glsurfaceview);
      surfaceView.setPreserveEGLContextOnPause(true);
      surfaceView.setEGLContextClientVersion(2);
      surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
      surfaceView.setRenderer(this);
      surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
      displayRotationHelper = new DisplayRotationHelper(this);
      tapHelper = new TapHelper(this);
      surfaceView.setOnTouchListener(tapHelper);
      resumeARCore();
    }

    @Override
    protected void onDestroy() {
        if (sharedSession != null) {
            sharedSession.close();
            sharedSession = null;
        }
        super.onDestroy();
    }

    private synchronized void waitUntilCameraCaptureSessionIsActive() {
        while (!captureSessionChangesPossible) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Unable to wait for a safe time to make changes to the capture session", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        waitUntilCameraCaptureSessionIsActive();
        startBackgroundThread();
        surfaceView.onResume();
        if (surfaceCreated) {
            openCamera();
        }

        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
      shouldUpdateSurfaceTexture.set(false);
      surfaceView.onPause();
      waitUntilCameraCaptureSessionIsActive();
      displayRotationHelper.onPause();
      pauseARCore();
      closeCamera();
      stopBackgroundThread();
      super.onPause();
    }

    private void resumeARCore() {
        if (sharedSession == null) {
            return;
        }
        if (!arcoreActive) {
            try {
                backgroundRenderer.suppressTimestampZeroRendering(false);
                sharedSession.resume();
                arcoreActive = true;
                sharedCamera.setCaptureCallback(cameraCaptureCallback, backgroundHandler);
            } catch (CameraNotAvailableException e) {
                Log.e(TAG, "Failed to resume ARCore session", e);
            }
        }
    }

    private void pauseARCore() {
        if (arcoreActive) {
            sharedSession.pause();
            isFirstFrameWithoutArcore.set(true);
            arcoreActive = false;
        }
    }

  private void setRepeatingCaptureRequest() {
    try {
      captureSession.setRepeatingRequest(
              previewCaptureRequestBuilder.build(), cameraCaptureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Failed to set repeating request", e);
    }
  }

    private void createCameraPreviewSession() {
        try {
            sharedSession.setCameraTextureName(backgroundRenderer.getTextureId());
            sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
            previewCaptureRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();
            surfaceList.add(cpuImageReader.getSurface());
            for (Surface surface : surfaceList) {
                previewCaptureRequestBuilder.addTarget(surface);
            }
            CameraCaptureSession.StateCallback wrappedCallback =
                    sharedCamera.createARSessionStateCallback(cameraSessionStateCallback, backgroundHandler);
            cameraDevice.createCaptureSession(surfaceList, wrappedCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("sharedCameraBackground");
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
                Log.e(TAG, "Interrupted while trying to join background handler thread", e);
            }
        }
    }

  private void openCamera() {
    if (cameraDevice != null) {
      return;
    }
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      CameraPermissionHelper.requestCameraPermission(this);
      return;
    }
    if (sharedSession == null) {
      try {
        sharedSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
      } catch (Exception e) {
        return;
      }
      errorCreatingSession = false;
      Config config = sharedSession.getConfig();
      config.setFocusMode(Config.FocusMode.AUTO);
      sharedSession.configure(config);
    }
    sharedCamera = sharedSession.getSharedCamera();
    cameraId = sharedSession.getCameraConfig().getCameraId();
    Size desiredCpuImageSize = sharedSession.getCameraConfig().getImageSize();
    cpuImageReader =
            ImageReader.newInstance(
                    desiredCpuImageSize.getWidth(),
                    desiredCpuImageSize.getHeight(),
                    ImageFormat.YUV_420_888,
                    2);
    cpuImageReader.setOnImageAvailableListener(this, backgroundHandler);
    sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReader.getSurface()));
    try {
      CameraDevice.StateCallback wrappedCallback =
              sharedCamera.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler);
      cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraId);
      if (Build.VERSION.SDK_INT >= 28) {
        keysThatCanCauseCaptureDelaysWhenModified = characteristics.getAvailableSessionKeys();
        if (keysThatCanCauseCaptureDelaysWhenModified == null) {

          keysThatCanCauseCaptureDelaysWhenModified = new ArrayList<>();
        }
      }
      captureSessionChangesPossible = false;
      cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler);
    } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
      Log.e(TAG, "Failed to open camera", e);
    }
  }
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            waitUntilCameraCaptureSessionIsActive();
            safeToExitApp.close();
            cameraDevice.close();
            safeToExitApp.block();
        }
        if (cpuImageReader != null) {
            cpuImageReader.close();
            cpuImageReader = null;
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {

    }
    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            Log.w(TAG, "onImageAvailable: Skipping null image.");
            return;
        }
        image.close();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        surfaceCreated = true;
        GLES20.glClearColor(0f, 0f, 0f, 1.0f);
        try {
            backgroundRenderer.createOnGlThread(this);
            planeRenderer.createOnGlThread(this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(this);

            virtualObject.createOnGlThread(this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow.createOnGlThread(
                    this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

            openCamera();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        displayRotationHelper.onSurfaceChanged(width, height);
    }

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    if (!shouldUpdateSurfaceTexture.get()) {
      return;
    }
    displayRotationHelper.updateSessionIfNeeded(sharedSession);
    try {
      onDrawFrameARCore();
    } catch (Throwable t) {
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

    public void onDrawFrameARCore() throws CameraNotAvailableException {
        if (!arcoreActive) {
            return;
        }
        if (errorCreatingSession) {
            return;
        }
        Frame frame = sharedSession.update();
        Camera camera = frame.getCamera();
        handleTap(frame, camera);
        backgroundRenderer.draw(frame);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }
        float[] projmtx = new float[16];
        camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);
        float[] viewmtx = new float[16];
        camera.getViewMatrix(viewmtx, 0);
        final float[] colorCorrectionRgba = new float[4];
        frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            pointCloudRenderer.update(pointCloud);
            pointCloudRenderer.draw(viewmtx, projmtx);
        }
        planeRenderer.drawPlanes(
                sharedSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);
        float scaleFactor = 1.0f;
        for (ColoredAnchor coloredAnchor : anchors) {
            if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                continue;
            }
            coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
        }
    }

    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {

                Trackable trackable = hit.getTrackable();

                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {


                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }
                    float[] objColor;
                    if (trackable instanceof Point) {
                        objColor = new float[]{66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof Plane) {
                        objColor = new float[]{139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
    }
}