package com.example.osmzhttpserver;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

public class SocketServer extends Thread {
    private Semaphore threadSemaphore;
    private Handler handler;
    private static final String TAG = "HttpServer";
    private ServerSocket serverSocket;
    public final int port = 12345;
    private boolean bRunning;
    private Context context;

    private static final String SERVER_ROOT = "/";
    private static final String DEFAULT_PAGE = "telemetry.html";

    public SocketServer(int maxThreads, Handler handler, Context context) {
        this.threadSemaphore = new Semaphore(maxThreads);
        this.handler = handler;
        this.context = context;
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
                        handleRequest(s);
                        threadSemaphore.release();
                    }
                }).start();
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error creating server socket: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException: " + e.getMessage());
        } finally {
            serverSocket = null;
            bRunning = false;

        }
    }

    private void handleRequest(Socket clientSocket) {
        try {
            BufferedInputStream input = new BufferedInputStream(clientSocket.getInputStream());
            OutputStream output = new BufferedOutputStream(clientSocket.getOutputStream());

            StringBuilder request = new StringBuilder();
            int nextChar;
            while ((nextChar = input.read()) != -1) {
                request.append((char) nextChar);
                if (request.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }

            String[] requestParts = request.toString().split("\\s+");

            if (requestParts.length >= 2) {
                String method = requestParts[0];
                String path = requestParts[1];

                if (path.equals("/streams/telemetry")) {
                    String event = "Telemetry request received from client: " + clientSocket.getInetAddress();
                    sendMessageToUI(event);
                    sendResponseHeader(output, 200, "application/json");

                    final int NUM_TELEMETRY_VALUES = 20;
                    final long INTERVAL_MS = 500;

                    for (int i = 0; i < NUM_TELEMETRY_VALUES; i++) {
                        sendTelemetryData(output);
                        try {
                            Thread.sleep(INTERVAL_MS);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Error while waiting: " + e.getMessage());
                        }
                    }

                    output.flush();
                    output.close();
                    input.close();
                    clientSocket.close();
                    return;
                }

                Log.d(TAG, "Request: " + method + " " + path);

                serveFile(output, path);
            } else {
                sendErrorResponse(output, 404, "Not Found");
            }

            output.flush();
            output.close();
            input.close();

            String event = "Request handled for client: " + clientSocket.getInetAddress();
            sendMessageToUI(event);
            clientSocket.close();

            Log.d("SERVER", "Socket Closed");
        } catch (IOException e) {
            Log.e(TAG, "Error handling request: " + e.getMessage());
        }
    }

    private void sendResponseHeader(OutputStream output, int i, String contentType) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(new Date());

        String response = "HTTP/1.0 " + i + " " + getResponseStatus(i) + "\r\n" +
                "Date: " + date + "\r\n" +
                "Content-Type: " + contentType + "\r\n\r\n";
        try {
            output.write(response.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Error sending response header: " + e.getMessage());
        }
    }

    private void sendTelemetryData(OutputStream output) throws IOException {
        JSONObject telemetryData = Telemetry.generateRandomTelemetryData();
        String serializedData = telemetryData.toString();
        sendResponseHeader(output, 200, "application/json");
        output.write(serializedData.getBytes());
        output.write("\n".getBytes());
    }

    private void sendMessageToUI(String event) {
        Message message = handler.obtainMessage();
        message.obj = event;
        handler.sendMessage(message);
    }

    private void serveFile(OutputStream output, String path) throws IOException {
        try {
            InputStream inputStream = context.getAssets().open("telemetry.html");
            String contentType = getContentType(path);
            byte[] fileContent = readFileContent(inputStream);
            if (contentType != null && fileContent != null) {
                sendResponse(output, 200, contentType, fileContent);
            } else {
                Log.e(TAG, "Failed to read file: telemetry.html");
                sendErrorResponse(output, 500, "Internal Server Error");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error serving telemetry HTML file: " + e.getMessage());
            sendErrorResponse(output, 500, "Internal Server Error");
        }
    }

    private void sendResponse(OutputStream output, int statusCode, String contentType, byte[] content) throws IOException {
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

    private String getResponseStatus(int statusCode) {
        switch (statusCode) {
            case 200:
                return "OK";
            case 404:
                return "Not Found";
            case 500:
                return "Internal Server Error";
            default:
                return "Internal Server Error";
        }
    }

    private void sendErrorResponse(OutputStream output, int statusCode, String statusMessage) throws IOException {
        String response = "HTTP/1.0 " + statusCode + " " + statusMessage + "\r\n\r\n";
        output.write(response.getBytes());
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html" + "; charset=utf-8";
        }
        return path;
    }

    private byte[] readFileContent(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}