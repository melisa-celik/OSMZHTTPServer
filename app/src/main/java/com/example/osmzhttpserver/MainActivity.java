package com.example.osmzhttpserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private SocketServer s;
    private static final int READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    private static TextView logTextView;
    private static final int MAX_THREADS = 5;
    private Camera mCamera;
    private CameraPreview mPreview;
    private CameraActivity cameraActivity;
//    private HTTPD httpServer;

    private static Handler handler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(@NonNull android.os.Message msg) {
            String event = (String) msg.obj;
            appendLog(event);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn1 = (Button)findViewById(R.id.button1);
        Button btn2 = (Button)findViewById(R.id.button2);
        logTextView = findViewById(R.id.logTextView);

        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);

//        initializeCamera();

//        startService(new Intent(this, TelemetryDataService.class));

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
//            initializeCamera();
            initializeServer();
//            startHttpServer();
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
        // Check if the device has a camera
        if (!checkCameraHardware(this)) {
            Log.e(TAG, "No camera found.");
            return;
        }

        try {
            // Create an instance of Camera
            mCamera = getCameraInstance();
            if (mCamera != null) {
                // Create our Preview view and set it as the content of our activity.
                mPreview = new CameraPreview(this, mCamera);
                FrameLayout preview = findViewById(R.id.camera_preview);
                preview.addView(mPreview);

                // Schedule the timer to take a picture every second
                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        // Trigger the camera to take a picture
                        mCamera.takePicture(null, null, mPicture);
                    }
                }, 0, 1000); // Delay of 0 milliseconds, repeat every 1000 milliseconds (1 second)
            } else {
                Log.e(TAG, "Failed to initialize camera.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera: " + e.getMessage());
        }
    }

    private void initializeServer() {
        if (s == null) {
            changeFilePermissions();
            s = new SocketServer(MAX_THREADS, handler, getApplicationContext(), mCamera);
            s.start();
        } else {
            Log.d(TAG, "Server is already running.");
        }
    }

//    private void startHttpServer() {
//        if (httpServer == null) {
//            try {
//                httpServer = new HTTPD();
//            } catch (IOException e) {
//                Log.e(TAG, "Error starting HTTP server: " + e.getMessage());
//            }
//        } else {
//            Log.d(TAG, "HTTP server is already running.");
//        }
//    }

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
            int numCameras = Camera.getNumberOfCameras();
            if (numCameras > 0) {
                c = Camera.open(0); // Open the first camera
            } else {
                Log.e(TAG, "No cameras available");
            }
        }
        catch (Exception e){
            Log.e(TAG, "Error opening camera: " + e.getMessage());
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
        File file = new File(Environment.getExternalStorageDirectory(), "post.html");
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
//            startHttpServer();
        }
        if (v.getId() == R.id.button2) {
            if (s != null) {
                s.close();
//                stopHttpServer();
                try {
                    s.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

//    private void stopHttpServer() {
//        if (httpServer != null) {
//            httpServer.stop();
//            httpServer = null;
//        }
//    }

    static void sendMessageToHandler(String message) {
        Message msg = handler.obtainMessage();
        msg.obj = message;
        handler.sendMessage(msg);
    }

    private static void appendLog(String event) {
        if (logTextView != null) {
            logTextView.append("\n" + event);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION | requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
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
    protected void onResume() {
        super.onResume();
        if (mCamera == null) {
            mCamera = getCameraInstance();
            if (mPreview != null) {
                mPreview.setCamera(mCamera);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release(); // Release the camera for other applications
            mCamera = null;
        }
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