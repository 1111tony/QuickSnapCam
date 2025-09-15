package com.tony.quicksnap;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import android.hardware.camera2.CameraManager;
import android.graphics.Camera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_REQUEST_CODE = 200;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;
    private Size imageDimension;
    private ImageReader imageReader;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		dbHelper = new DatabaseHelper(this);

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(textureListener);

        Button captureButton = findViewById(R.id.capture_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        Button galleryButton = findViewById(R.id.gallery_button);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GalleryActivity.class));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, CAMERA_REQUEST_CODE);
        }
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    // ---------- add background thread helpers ----------
    private void startBackgroundThread() {
        if (mBackgroundThread != null && mBackgroundThread.isAlive()) return;
        mBackgroundThread = new HandlerThread("CameraBackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.d(TAG, "Background thread started");
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null) return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to join background thread", e);
            Thread.currentThread().interrupt();
        } finally {
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.d(TAG, "Background thread stopped");
        }
    }

    // ---------- lifecycle hooks ----------
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        // If the TextureView is already available, open the camera immediately.
        try {
            if (textureView != null && textureView.isAvailable()) {
                openCamera();
            } // otherwise openCamera() will be called by the SurfaceTextureListener
        } catch (Exception e) {
            Log.e(TAG, "Error resuming camera preview", e);
            Toast.makeText(this, "Unable to start camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        // Close camera first (stop preview / sessions), then stop background thread.
        try {
            closeCamera();
        } catch (Exception e) {
            Log.e(TAG, "Error pausing camera", e);
        }
        stopBackgroundThread();
        super.onPause();
    }

    // Close camera and release imageReader and session safely
    private void closeCamera() {
        try {
            if (cameraCaptureSessions != null) {
                cameraCaptureSessions.close();
                cameraCaptureSessions = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing capture session", e);
        }

        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing camera device", e);
        }

        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing image reader", e);
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            }
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Camera permission not granted");
                return;
            }
            manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: CameraAccessException", e);
            Toast.makeText(this, "Unable to access camera", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "openCamera: unexpected error", e);
            Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.w(TAG, "Camera disconnected");
            try {
                camera.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing camera on disconnect", e);
            } finally {
                cameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            try {
                camera.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing camera on error", e);
            } finally {
                cameraDevice = null;
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera error: " + error, Toast.LENGTH_SHORT).show());
        }
    };

    private void startCameraPreview() {
        try {
            if (cameraDevice == null) {
                Log.w(TAG, "startCameraPreview called with null cameraDevice");
                return;
            }

            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null)
                return;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            cameraCaptureSessions = session;

                            // inline updatePreview logic to avoid null issues
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                            try {
                                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),
                                        new CameraCaptureSession.CaptureCallback() {
                                        }, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "startCameraPreview: CameraAccessException while setting repeating request", e);
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Preview start failed", Toast.LENGTH_SHORT).show());
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "startCameraPreview: onConfigureFailed");
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to configure camera preview", Toast.LENGTH_SHORT).show());
                        }
                    }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "startCameraPreview: CameraAccessException", e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unable to start camera preview", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "startCameraPreview: unexpected error", e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera preview error", Toast.LENGTH_SHORT).show());
        }
    }

    private void takePicture() {
        if (cameraDevice == null) {
            Log.w(TAG, "takePicture: cameraDevice is null");
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
                }
            }

            int width = 640, height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            final File file = getOutputMediaFile();

            
        	imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                Log.w(TAG, "onImageAvailable: image is null");
                return;
            }

            // Get image bytes
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // Use EncryptedFileManager to save securely
            EncryptedFileManager efm = new EncryptedFileManager(MainActivity.this);

            // Save the image bytes as a file (you can use Base64 or UTF-8 if needed)
            // Here we'll save the raw bytes in a file
            efm.write(file.getName(), new String(bytes, StandardCharsets.ISO_8859_1)); // Use ISO_8859_1 to preserve bytes

            dbHelper.insertImage(file.getAbsolutePath());

            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved securely: " + file.getName(), Toast.LENGTH_SHORT).show());

        } catch (Exception e) {
            Log.e(TAG, "onImageAvailable: error saving image", e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to save image", Toast.LENGTH_SHORT).show());
        } finally {
            if (image != null)
                image.close();
        }
    }
}, mBackgroundHandler);
        	
        	
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        }, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "takePicture: CameraAccessException while capturing", e);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        Log.e(TAG, "takePicture: unexpected error while capturing", e);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Capture error", Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "takePicture: onConfigureFailed");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to configure capture", Toast.LENGTH_SHORT).show());
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "takePicture: CameraAccessException", e);
            Toast.makeText(this, "Unable to take picture", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "takePicture: unexpected error", e);
            Toast.makeText(this, "Capture error", Toast.LENGTH_SHORT).show();
        }
    }

    private File getOutputMediaFile() {
        File mediaStorageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!mediaStorageDir.exists()) {
            boolean ok = mediaStorageDir.mkdirs();
            if (!ok) {
                Log.w(TAG, "getOutputMediaFile: failed to create directory " + mediaStorageDir.getAbsolutePath());
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(mediaStorageDir, "Snap_" + timeStamp + ".jpg.enc");
    }
}