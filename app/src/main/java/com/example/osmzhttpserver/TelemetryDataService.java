package com.example.osmzhttpserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class TelemetryDataService extends Service {
    private static final long INTERVAL = 10 * 1000; // 10 seconds interval for data collection
    private Timer timer;
    private TelemetryDataCollector telemetryDataCollector;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startDataCollection();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopDataCollection();
        super.onDestroy();
    }

    private void startDataCollection() {
        telemetryDataCollector = new TelemetryDataCollector(getApplicationContext());
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Collect telemetry data
                telemetryDataCollector.collectTelemetryData(new TelemetryDataCollector.TelemetryDataCallback() {
                    @Override
                    public void onTelemetryDataReceived(JSONObject telemetryData) {
                        // Handle received telemetry data (e.g., send it over the network)
                        // You can use the handler to send the telemetry data to the main thread if needed
                    }
                });
            }
        }, 0, INTERVAL);
    }

    private void stopDataCollection() {
        if (timer != null) {
            timer.cancel();
        }
        if (telemetryDataCollector != null) {
            // Cleanup resources if needed
        }
    }
}
