package com.example.camx;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.camx.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import android.widget.VideoView;

import com.example.camx.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    ProcessCameraProvider cameraProvider;

    private ActivityMainBinding binding;
    private VideoCapture<Recorder> vidCapture;
    private Recorder recorder;

    private Recording recording;

    private static final int REQUEST_CODE_MANAGE_EXTERNAL_STORAGE=102;
    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (allPermissionsGranted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(); // Call this function for Android 11+
            } else {
                startCamera();
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        binding.btnVid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (binding.btnVid.getText().equals("Stop Video")) {

                    if (recording == null) return;

                    recording.stop();
                    recording = null;
                    binding.btnVid.setText("Start Video");
                } else {
                    captureVid();
                }
            }
        });

        binding.btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSavedVideo();
            }
        });

        ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderListenableFuture.get();
                    startCameraX(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void getSavedVideo() {
        String videoFilePath = getPathFromUri(videoUri);

        if (videoFilePath != null) {
            // Assuming you have a VideoView with the id "videoView" in your layout
            VideoView videoView = findViewById(R.id.videoView);

            // Set the video file path for the VideoView
            videoView.setVideoPath(videoFilePath);

            // Start playing the video
            videoView.start();
        } else {
            // Handle the case where no video file path is found
            Log.d("VideoPath", "Video file path is null");
        }
    }



    private void captureVid() {
        if (vidCapture == null) return;

        String name = System.currentTimeMillis() + "";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CamX");

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(contentValues).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling ActivityCompat#requestPermissions here to request the missing permissions
            return;
        }

        recording = vidCapture.getOutput().prepareRecording(MainActivity.this, mediaStoreOutputOptions).withAudioEnabled().start(ContextCompat.getMainExecutor(MainActivity.this),
                new Consumer<VideoRecordEvent>() {
                    @Override
                    public void accept(VideoRecordEvent videoRecordEvent) {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            binding.btnVid.setText("Stop Video");
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            if (((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                if (recording != null) recording.stop();
                                recording = null;
                                return;
                            }

                            // The video recording is successful, and you can access its URI here
                            videoUri = ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri();

                            String videoPath = getPathFromUri(videoUri);
                           // Toast.makeText(MainActivity.this, "Video Saved to Your Gallery", Toast.LENGTH_SHORT).show();

                            binding.textPath.setText(videoPath);

                           // Toast.makeText(MainActivity.this, "Video Path: " + videoPath, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private String getPathFromUri(Uri uri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return uri.getPath();
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderListenableFuture.get();
                    startCameraX(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startCameraX(ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        recorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build();
        vidCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, vidCapture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check for "MANAGE_EXTERNAL_STORAGE" permission
            if (checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request the permission via app settings
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_MANAGE_EXTERNAL_STORAGE);
            }
        } else {
            // For devices running Android 11 (API level 30) and lower, use your existing permission handling logic
            if (!allPermissionsGranted()) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MANAGE_EXTERNAL_STORAGE) {
            // Check if the user granted the "MANAGE_EXTERNAL_STORAGE" permission
            if (checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // The permission was granted, you can proceed with your operations that require storage access
                startCamera();
            } else {
                // The user did not grant the permission
                Toast.makeText(this, "Storage permission not granted.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
