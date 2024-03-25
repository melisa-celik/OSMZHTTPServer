package com.example.osmzhttpserver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class Telemetry implements LocationListener, SensorEventListener {
    private SensorManager sensorManager;
    private Context context;
    private Map<String, Object> data;
    private Handler handler;
    private Runnable updateTelemetryRunnable;
    private static final String TAG = "Telemetry";

    public Telemetry(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        registerSensorListeners();
        data = new HashMap<>();
        startLocationUpdates();
        updateTelemetryRunnable = new Runnable() {
            @Override
            public void run() {
                updateTelemetry();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(updateTelemetryRunnable);
    }

    private void updateTelemetry() {
        data.put("time", System.currentTimeMillis());
        JSONObject json = new JSONObject(data);
        Message message = handler.obtainMessage(1, json.toString());
        message.sendToTarget();
    }

    private void registerSensorListeners() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public static JSONObject generateRandomTelemetryData() {
        JSONObject telemetryData = new JSONObject();
        try {
            telemetryData.put("temperature", generateRandomTemperature());
            telemetryData.put("humidity", generateRandomHumidity());
            telemetryData.put("pressure", generateRandomPressure());
            telemetryData.put("latitude", generateRandomLatitude());
            telemetryData.put("longitude", generateRandomLongitude());
            telemetryData.put("acceleration", generateRandomAcceleration());
            telemetryData.put("gyroscope", generateRandomGyroscope());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating telemetry data: " + e.getMessage());
        }
        return telemetryData;
    }

    public static double generateRandomTemperature() {
        Random random = new Random();
        return Math.min(35.0, Math.max(20.0, 20.0 + random.nextFloat() * 15));
    }

    public static int generateRandomHumidity() {
        Random random = new Random();
        return Math.min(100, Math.max(0, random.nextInt(100)));
    }

    public static double generateRandomPressure() {
        Random random = new Random();
        return Math.min(1100, Math.max(900, 900 + random.nextFloat() * 200));
    }

    public static double generateRandomLatitude() {
        Random random = new Random();
        return Math.min(52.0, Math.max(48.0, 48.0 + random.nextDouble() * 4));
    }

    public static double generateRandomLongitude() {
        Random random = new Random();
        return Math.min(14.0, Math.max(10.0, 10.0 + random.nextDouble() * 4));
    }

    public static String generateRandomAcceleration() {
        Random random = new Random();
        float[] acceleration = new float[3];
        for (int i = 0; i < 3; i++) {
            acceleration[i] = Math.min(1.0f, Math.max(0.0f, random.nextFloat()));
        }
        return Arrays.toString(acceleration);
    }

    public static String generateRandomGyroscope() {
        Random random = new Random();
        float[] gyroscope = new float[3];
        for (int i = 0; i < 3; i++) {
            gyroscope[i] = Math.min(1.0f, Math.max(-1.0f, random.nextFloat() * 2 - 1));
        }
        return Arrays.toString(gyroscope);
    }

    private void startLocationUpdates() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        collectData("latitude", location.getLatitude());
        collectData("longitude", location.getLongitude());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                collectData("acceleration", event.values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                collectData("gyroscope", event.values);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    public void stopTelemetryDataCollector() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    public void sendTelemetryData(String host, int port) {
        JSONObject json = new JSONObject(data);
        String serializedData = json.toString();
        try (Socket socket = new Socket(host, port);
             OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(serializedData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void collectData(String key, Object value) {
        data.put(key, value);
    }

    public Map<String, Object> getTelemetryDataMap() {
        return data;
    }
}