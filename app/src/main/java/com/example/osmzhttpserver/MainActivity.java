package com.example.osmzhttpserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.io.File;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "HttpServer";
    private SocketServer s;
    private static final int READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int MAX_THREADS = 5;
    private TextView logTextView;
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

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            initializeServer();
        }
    }

    private void initializeServer() {
        changeFilePermissions();
        s = new SocketServer(MAX_THREADS, handler, this);
        s.start();
    }

    private void changeFilePermissions() {
        File file = new File(Environment.getExternalStorageDirectory(), "Documents/index.html");
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


// Because I always encountered Error serving file: Permission denied error, I added the following code to the MainActivity.java file using AI - although I am ashamed to admit that, so sorry:
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        switch (requestCode) {
//
//            case READ_EXTERNAL_STORAGE:
//                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
//                    s = new SocketServer();
//                    s.start();
//                }
//                break;
//
//            default:
//                break;
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeServer();
            } else {
                Log.e(TAG, "Permission denied.");
            }
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
    }
}