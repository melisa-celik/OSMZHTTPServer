package com.example.osmzhttpserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class TelemetryDataCollector implements SensorEventListener {
    private SensorManager sensorManager;
    private JSONObject telemetryData;
    private FusedLocationProviderClient fusedLocationClient;
    private static final long LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_LOCATION_UPDATE_INTERVAL = 5000; // 5 seconds
    private double latitude;
    private double longitude;
    private Context context;
    private static final String TAG = "TelemetryDataCollector";

    public TelemetryDataCollector(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        telemetryData = new JSONObject();
        registerSensorListeners();
    }

    private void registerSensorListeners() {
        // Register listeners for required sensors
        // Example:
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        // Register for other required sensors
    }


    private void unregisterSensorListeners() {
        sensorManager.unregisterListener((SensorListener) this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Update telemetryData with sensor data
        // Example:
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            try {
                telemetryData.put("accelerometer_x", event.values[0]);
                telemetryData.put("accelerometer_y", event.values[1]);
                telemetryData.put("accelerometer_z", event.values[2]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // Handle other sensor data
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
    }

    public JSONObject getTelemetryData() {
        return telemetryData;
    }

    public void collectTelemetryData(final TelemetryDataCallback callback) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_LOCATION_UPDATE_INTERVAL);

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, handle it here
            // For example, request permission from the user
            // ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                try {
                    JSONObject telemetryJson = new JSONObject();
                    telemetryJson.put("latitude", location.getLatitude());
                    telemetryJson.put("longitude", location.getLongitude());
                    callback.onTelemetryDataReceived(telemetryJson); // Call the callback with the telemetry data
                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
            } else {
                requestLocationUpdates(locationRequest, callback);
            }
        }).addOnFailureListener(e -> {
            e.printStackTrace();
        });
    }

    private void requestLocationUpdates(LocationRequest locationRequest, TelemetryDataCallback callback) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, handle it here
            // For example, request permission from the user
            // ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        try {
                            JSONObject telemetryJson = new JSONObject();
                            telemetryJson.put("latitude", location.getLatitude());
                            telemetryJson.put("longitude", location.getLongitude());
                            callback.onTelemetryDataReceived(telemetryJson); // Call the callback with the telemetry data
                        } catch (JSONException e) {
                            e.printStackTrace();
                            // Handle JSONException
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }, Looper.getMainLooper());
    }

    private void sendJSONResponse(OutputStream output, int statusCode, JSONObject json) throws IOException {
        output.write(("HTTP/1.1 " + statusCode + " OK\r\n").getBytes());
        output.write(("Content-Type: application/json\r\n").getBytes());
        output.write(("\r\n").getBytes());
        output.write((json.toString()).getBytes());
    }

    // Define a callback interface
    public interface TelemetryDataCallback {
        void onTelemetryDataReceived(JSONObject telemetryData) throws IOException;
    }

    public JSONObject collectTelemetryData() {
        try {
            Location lastLocation = fusedLocationClient.getLastLocation().getResult();
            if (lastLocation != null) {
                JSONObject telemetryJson = new JSONObject();
                telemetryJson.put("latitude", lastLocation.getLatitude());
                telemetryJson.put("longitude", lastLocation.getLongitude());
                // Add other telemetry data to the JSON object as needed
                return telemetryJson;
            } else {
                Log.e(TAG, "Last location is null");
            }
        } catch (SecurityException | JSONException e) {
            Log.e(TAG, "Error collecting telemetry data: " + e.getMessage());
        }
        return null;
    }
}
