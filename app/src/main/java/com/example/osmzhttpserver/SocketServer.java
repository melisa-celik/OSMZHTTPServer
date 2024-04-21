package com.example.osmzhttpserver;

import android.icu.number.NumberFormatter;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class  SocketServer extends Thread {
    private static final String TAG = "HttpServer";
    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    private static final String SERVER_ROOT = "/";

    private static final String DEFAULT_PAGE = "index.html";

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

                handleRequest(s);

                s.close();
                Log.d("SERVER", "Socket Closed");
            }
        } catch (IOException e) {
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
            String method = requestParts[0];
            String path = requestParts[1];

            Log.d(TAG, "Request: " + method + " " + path);

            serveFile(output, path);

            output.flush();
            output.close();
            input.close();
        } catch (IOException e) {
            Log.e(TAG, "Error handling request: " + e.getMessage());
        }
    }

    private void serveFile(OutputStream output, String path) throws IOException {
        try {
            File file;
            if (path.equals("/")) {
                file = new File(Environment.getExternalStorageDirectory() + SERVER_ROOT + DEFAULT_PAGE);
            } else {
                file = new File(Environment.getExternalStorageDirectory() + SERVER_ROOT);
            }

            if (file.exists() && file.isFile()) {
                String contentType = getContentType(file);
                byte[] fileContent = readFileContent(file);
                if (contentType != null && fileContent != null) {
                    sendResponse(output, 200, contentType, fileContent);
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