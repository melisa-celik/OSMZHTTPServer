package com.example.osmzhttpserver;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.widget.TextView;

import android.widget.FrameLayout;

import android.hardware.Camera;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private SocketServer s;
    private static final int READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;

    private static final int REQUEST_CAMERA_PERMISSION = 101;

    private static final int MAX_THREADS = 5;
    private TextView logTextView;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private Camera mCamera;
    private CameraPreview mPreview;

    private Handler handler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(@NonNull android.os.Message msg) {
            String event = (String) msg.obj;
            logTextView.append("\n" + event);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        logTextView = findViewById(R.id.logTextView);
        Button btn1 = findViewById(R.id.button1);
        Button btn2 = findViewById(R.id.button2);
        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);

        initializeCamera(); // Initialize camera object

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }
        else if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }else {
            initializeCamera();
            initializeServer();
        }

        if (!checkCameraHardware(this)) {
            Log.e(TAG, "No camera found.");
        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }

    private void initializeCamera() {
        mCamera = Camera.open();
//        CameraPreview cameraPreview = findViewById(R.id.camera_preview);
//        cameraPreview.startPreview();
        // Check if the device has a camera
        if (!checkCameraHardware(this)) {
            Log.e(TAG, "No camera found.");
            return;
        }

        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera != null) {
            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = findViewById(R.id.camera_preview);
            preview.addView(mPreview);
            mPreview.startPreview();

            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        mCamera.takePicture(null, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {
                                try {
//                                    jpegStream.write("--OSMZ_boundary\r\n".getBytes());
//                                    jpegStream.write("Content-Type: image/jpeg\r\n\r\n".getBytes());
//                                    jpegStream.write(data);
//                                    jpegStream.write("\r\n".getBytes());
//                                    output.write(jpegStream.toByteArray());
//                                    output.flush();
//                                    jpegStream.reset();
                                    mCamera.startPreview();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                    try {
                        Thread.sleep(333);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            Timer timer = new Timer();
            timer.schedule(timerTask, 5000, 1000);
        } else {
            Log.e(TAG, "Failed to initialize camera.");
        }
    }

    private void initializeServer() {
        changeFilePermissions();
        s = new SocketServer(MAX_THREADS, handler, this, mCamera);
        s.start();
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "Saving image...");
            File pictureFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath().toString() + "/camera.jpg");
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };

    private void changeFilePermissions() {
        File file = new File(Environment.getExternalStorageDirectory(), "index.html");
        if (file.exists()) {
            file.setReadable(true);
            file.setWritable(true);
            file.setExecutable(true);
        } else {
            Log.e(TAG, "File not found: " + file.getAbsolutePath());
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button1) {
            initializeServer();
        }
        if (v.getId() == R.id.button2) {
            if (s != null) {
                s.close();
                try {
                    s.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION | requestCode == REQUEST_READ_EXTERNAL_STORAGE | requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
                initializeServer();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }


    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Denied")
                .setMessage("Without camera permission, this app cannot function properly. Please grant the permission.")
                .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAppSettings();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (s != null) {
            s.close();
            try {
                s.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }
}