package com.example.osmzhttpserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class TelemetryDataCollector {
    private FusedLocationProviderClient fusedLocationClient;
    private static final long LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_LOCATION_UPDATE_INTERVAL = 5000; // 5 seconds
    private double latitude;
    private double longitude;
    private Context context;
    private static final String TAG = "TelemetryDataCollector";

    public TelemetryDataCollector(Context context) {
        this.context = context;
        fusedLocationClient = new FusedLocationProviderClient(context);
    }

    public void collectTelemetryData(final OutputStream output) {
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
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                try {
                    sendTelemetryData(output);
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle IOException
                }
            } else {
                requestLocationUpdates(locationRequest, output);
            }
        }).addOnFailureListener(e -> {
            e.printStackTrace();
            // Handle failure
        });
    }

    private void requestLocationUpdates(LocationRequest locationRequest, OutputStream output) {
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
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        try {
                            sendTelemetryData(output);
                        } catch (IOException e) {
                            e.printStackTrace();
                            // Handle IOException
                        }
                    }
                }
            }
        }, Looper.getMainLooper());
    }

    private void sendTelemetryData(OutputStream output) throws IOException {
        JSONObject telemetryJson = new JSONObject();
        try {
            telemetryJson.put("latitude", latitude);
            telemetryJson.put("longitude", longitude);
            // Add other telemetry data to the JSON object as needed

            // Send JSON response
            sendJSONResponse(output, 200, telemetryJson);
        } catch (JSONException e) {
            e.printStackTrace();
            // Handle JSONException
        }
    }

    private void sendJSONResponse(OutputStream output, int statusCode, JSONObject json) throws IOException {
        output.write(("HTTP/1.1 " + statusCode + " OK\r\n").getBytes());
        output.write(("Content-Type: application/json\r\n").getBytes());
        output.write(("\r\n").getBytes());
        output.write((json.toString()).getBytes());
    }

    public JSONObject collectTelemetryData() {
        JSONObject telemetryJson = new JSONObject();
        try {
            Location lastLocation = fusedLocationClient.getLastLocation().getResult();
            if (lastLocation != null) {
                double latitude = lastLocation.getLatitude();
                double longitude = lastLocation.getLongitude();
                telemetryJson.put("latitude", latitude);
                telemetryJson.put("longitude", longitude);
            }
            return telemetryJson;
        } catch (JSONException | SecurityException e) {
            Log.e(TAG, "Error collecting telemetry data: " + e.getMessage());
            return null;
        }
    }
}
