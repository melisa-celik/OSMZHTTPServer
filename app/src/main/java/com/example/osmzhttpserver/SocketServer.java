package com.example.osmzhttpserver;
import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final String DEFAULT_PAGE = "index.html";

    private static final String BOUNDARY = "OSMZ_BOUNDARY";

    private ExecutorService executorService;

    private Camera mCamera;

    public SocketServer(int maxThreads, Handler handler, Context context, Camera camera) {
        this.threadSemaphore = new Semaphore(maxThreads);
        this.handler = handler;
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(maxThreads);
        this.mCamera = camera;
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
                new Thread(() -> {
                    handleRequest(s);
                    threadSemaphore.release();
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
                if (path.equals("/camera/stream")) {
//                    serveMJPEGStream(output);
                }
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

    private void serveMJPEGStream(Socket clientSocket) {
        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

//            sendResponseHeader(output, 200, "multipart/x-mixed-replace; boundary=OSMZ_boundary");

            while (true) {
//                ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
                // Capture frame
                final byte[][] frameData = {new byte[1024]};
                mCamera = Camera.open();
                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        frameData[0] = data;
                    }
                });

                String header = "HTTP/1.0 200 OK\r\n"
                        + "Server: NanoHTTPD/2.0\r\n"
                        + "Content-Type: multipart/x-mixed-replace; boundary=--MJPG\r\n"
                        + "\r\n";

                // Write header
                outputStream.write(header.getBytes());

                // Write MJPEG frame
                dataOutputStream.writeBytes("--MJPG\r\n");
                dataOutputStream.writeBytes("Content-Type: image/jpeg\r\n");
                dataOutputStream.writeBytes("Content-Length: " + frameData[0].length + "\r\n");
                dataOutputStream.writeBytes("\r\n");
                dataOutputStream.write(frameData[0]);
                dataOutputStream.writeBytes("\r\n");

//                if (mCamera != null) {
//                    mCamera.takePicture(null, null, new Camera.PictureCallback() {
//                        @Override
//                        public void onPictureTaken(byte[] data, Camera camera) {
//                            try {
//                                jpegStream.write("--OSMZ_boundary\r\n".getBytes());
//                                jpegStream.write("Content-Type: image/jpeg\r\n\r\n".getBytes());
//                                jpegStream.write(data);
//                                jpegStream.write("\r\n".getBytes());
//                                output.write(jpegStream.toByteArray());
//                                output.flush();
//                                jpegStream.reset();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    });
//                }
//                Thread.sleep(333);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommand(OutputStream output, String command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder commandOutput = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            commandOutput.append(line).append("<br>");
        }

        sendResponseHeader(output, 200, "text/html");
        String response = "<html><body><pre>" + commandOutput.toString() + "</pre></body></html>";
        output.write(response.getBytes());

        process.waitFor();
    }

    private boolean isDirectory(String path) {
        try {
            String[] files = context.getAssets().list(path);
            return files != null && files.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void serveDirectory(OutputStream output, String path) throws IOException {
        File directory = new File(path);
        File[] files = directory.listFiles();
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html><body><h1>Directory Listing</h1><ul>");

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String filePath = file.getAbsolutePath();
                htmlBuilder.append("<li><a href=\"").append(filePath).append("\">").append(fileName).append("</a></li>");
            }
        }

        htmlBuilder.append("</ul></body></html>");

        sendResponseHeader(output, 200, "text/html");
        output.write(htmlBuilder.toString().getBytes());
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
            File file = new File(context.getFilesDir(), DEFAULT_PAGE);
            InputStream inputStream = new FileInputStream(file);
            String contentType = getContentType(DEFAULT_PAGE);
            byte[] fileContent = readFileContent(inputStream);
            if (contentType != null && fileContent != null) {
                sendResponse(output, 200, contentType, fileContent);
            } else {
                Log.e(TAG, "Failed to read file: " + DEFAULT_PAGE);
                sendErrorResponse(output, 500, "Internal Server Error");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error serving file: " + DEFAULT_PAGE, e);
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