package com.example.osmzhttpserver;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

public class  SocketServer extends Thread {
    private static final String TAG = "HttpServer";
    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;
    private static final String SERVER_ROOT = "/";
    private static final String DEFAULT_PAGE = "telemetry.html";
    private Handler handler;
    private Semaphore threadSemaphore;
    private TelemetryDataCollector telemetryDataCollector;


    public SocketServer(int maxThread, Handler handler, Context context) {
        threadSemaphore = new Semaphore(maxThread);
        this.handler = handler;
        this.telemetryDataCollector = new TelemetryDataCollector(context);
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.d("SERVER", "Error, probably interrupted in accept(), see log");
            e.printStackTrace();
        }
        bRunning = false;
    }

    @Override
    public void run() {
        try {
            Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);
            bRunning = true;

            while (bRunning) {
                Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();
                Log.d("SERVER", "Socket Accepted");

                threadSemaphore.acquire();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            handleRequest(s);
                            threadSemaphore.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling request: " + e.getMessage());
                        }
                    }
                }).start();

//                s.close();
                Log.d("SERVER", "Socket Closed");
            }
        } catch (IOException | InterruptedException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error creating server socket: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            serverSocket = null;
            bRunning = false;

        }
    }

    private void handleRequest(Socket s) {
        try {
            if (!threadSemaphore.tryAcquire()) {
                sendErrorResponse(s.getOutputStream(), 503, "Server too busy");
                s.close();
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            OutputStream out = s.getOutputStream();
            String request = in.readLine();

            if (request != null && request.trim().length() > 0) {
                Log.d(TAG, "Request: " + request);

                String[] tokens = request.split(" ");
                if (tokens.length < 2) {
                    Log.e(TAG, "Invalid request: " + request);
                    return;
                }

                String method = tokens[0];
                String uri = tokens[1];

                if (!method.equalsIgnoreCase("GET")) {
                    Log.e(TAG, "Unsupported method: " + method);
                    sendErrorResponse(out, 501, "Not Implemented");
                    return;
                }

                if (uri.equals("/streams/telemetry")) {
                    serveStaticFile(out, "telemetry.html");
                    handleTelemetryRequest(out);
                    return;
                }
            }

            // Handle other URIs or return an error response
            sendErrorResponse(out, 404, "Not Found");
        } catch (IOException e) {
            Log.e(TAG, "Error handling request: " + e.getMessage());
        } finally {
            try {
                s.close();
                threadSemaphore.release();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket: " + e.getMessage());
            }
        }
    }

    private void handleTelemetryRequest(OutputStream output) throws IOException {
//        telemetryDataCollector.collectTelemetryData();
        JSONObject telemetryData = telemetryDataCollector.getTelemetryData();
        if (telemetryData != null) {
            sendJSONResponse(output, 200, telemetryData);
        } else {
            sendErrorResponse(output, 500, "Error collecting telemetry data");
        }
    }

    private void sendJSONResponse(OutputStream output, int statusCode, JSONObject json) throws IOException {
        output.write(("HTTP/1.1 " + statusCode + " OK\r\n").getBytes());
        output.write(("Content-Type: application/json\r\n").getBytes());
        output.write(("Access-Control-Allow-Origin: *\r\n").getBytes()); // Add CORS header to allow cross-origin requests
        output.write(("\r\n").getBytes());
        output.write((json.toString()).getBytes());
    }

    private void serveStaticFile(OutputStream output, String fileName) throws IOException {
        File file = new File(Environment.getExternalStorageDirectory() + SERVER_ROOT + fileName);
        if (!file.exists()) {
            sendErrorResponse(output, 404, "File Not Found");
            return;
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    private byte[] readFileData(File file) throws IOException {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return outputStream.toByteArray();
    }

    private String getMimeType(String filePath) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(filePath);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    private String getUserAgent(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("User-Agent:")) {
                return line.substring("User-Agent:".length()).trim();
            }
        }
        return "Unknown";
    }

    private void serveFile(OutputStream output, String path) throws IOException {
        try {
            File file;
            if (path.equals("/streams/telemetry")) {
                file = new File(Environment.getExternalStorageDirectory() + SERVER_ROOT + DEFAULT_PAGE);
            } else {
                file = new File(Environment.getExternalStorageDirectory() + SERVER_ROOT + path);
            }

            if (file.exists() && file.isFile()) {
                String contentType = getContentType(file);
                byte[] fileContent = readFileContent(file);
                if (contentType != null && fileContent != null) {
                    String mimeType = null;
                    sendResponse(output, 200, contentType, mimeType, fileContent);
                } else {
                    Log.e(TAG, "Failed to read file: " + file.getAbsolutePath());
                    sendErrorResponse(output, 500, "Internal Server Error");
                }
            } else {
                Log.d(TAG, "File not found: " + file.getAbsolutePath());
                sendErrorResponse(output, 404, "Not Found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error serving file: " + e.getMessage());
            sendErrorResponse(output, 500, "Internal Server Error");
        }
    }

    private void sendResponse(OutputStream output, int statusCode, String contentType, String mimeType, byte[] content) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(new Date());

        String response = "HTTP/1.0 " + statusCode + " " + getResponseStatus(statusCode) + "\r\n" +
                "Date: " + date + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + content.length + "\r\n\r\n";
        output.write(response.getBytes());
        output.write(content);
    }

    private void sendErrorResponse(OutputStream output, int statusCode, String statusMessage) throws IOException {
        String response = "HTTP/1.0 " + statusCode + " " + statusMessage + "\r\n\r\n";
        output.write(response.getBytes());
    }

    private String getContentType(File file) {
        String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
        String contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        if (contentType == null) {
            contentType = "text/html";
        }
        return contentType;
    }

    private byte[] readFileContent(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = bis.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }
        bis.close();
        fis.close();
        return bos.toByteArray();
    }

    private String getResponseStatus(int statusCode) {
        switch (statusCode) {
            case 200:
                return "OK";
            case 404:
                return "Not Found";
            case 500:
                return "Internal Server Error";
            default:
                return "Unknown";
        }
    }
}