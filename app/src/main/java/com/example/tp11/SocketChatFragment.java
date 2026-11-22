package com.example.tp11;

import android.content.Context;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment handling TCP socket chat in either Client Mode or Server Mode.
 * Binds sockets directly to WifiNetworkSpecifier network if active.
 */
public class SocketChatFragment extends Fragment {

    private RadioGroup rgMode;
    private RadioButton rbClientMode;
    private RadioButton rbServerMode;
    private TextInputLayout tilIpAddress;
    private EditText etIpAddress;
    private EditText etPort;
    private TextView tvSocketStatus;
    private MaterialButton btnConnectSocket;
    private RecyclerView rvChatMessages;
    private EditText etMessageInput;

    private ChatAdapter chatAdapter;

    // Concurrency & Thread Pool
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // Connection state
    private boolean isServerMode = false;
    private boolean isConnectedOrRunning = false;

    // Client Mode Socket references
    private Socket clientSocket;
    private PrintWriter clientWriter;
    private BufferedReader clientReader;

    // Server Mode Socket references
    private ServerSocket serverSocket;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_socket_chat, container, false);

        rgMode = view.findViewById(R.id.rgMode);
        rbClientMode = view.findViewById(R.id.rbClientMode);
        rbServerMode = view.findViewById(R.id.rbServerMode);
        tilIpAddress = view.findViewById(R.id.tilIpAddress);
        etIpAddress = view.findViewById(R.id.etIpAddress);
        etPort = view.findViewById(R.id.etPort);
        tvSocketStatus = view.findViewById(R.id.tvSocketStatus);
        btnConnectSocket = view.findViewById(R.id.btnConnectSocket);
        rvChatMessages = view.findViewById(R.id.rvChatMessages);
        etMessageInput = view.findViewById(R.id.etMessageInput);
        MaterialButton btnSendMessage = view.findViewById(R.id.btnSendMessage);

        rvChatMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatAdapter = new ChatAdapter();
        rvChatMessages.setAdapter(chatAdapter);

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            isServerMode = (checkedId == R.id.rbServerMode);
            updateModeUi();
        });

        btnConnectSocket.setOnClickListener(v -> {
            if (isConnectedOrRunning) {
                disconnectOrStop();
            } else {
                startConnectionOrServer();
            }
        });

        btnSendMessage.setOnClickListener(v -> sendMessage());

        updateModeUi();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disconnectOrStop();
    }

    private void updateModeUi() {
        if (isConnectedOrRunning) return;

        if (isServerMode) {
            tilIpAddress.setHint("Local Server IP");
            etIpAddress.setText(getLocalIpAddress());
            etIpAddress.setEnabled(false);
            btnConnectSocket.setText("Start Server");
            tvSocketStatus.setText("Server Stopped");
            tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
        } else {
            tilIpAddress.setHint("Target IP Address");
            etIpAddress.setEnabled(true);
            btnConnectSocket.setText("Connect");
            tvSocketStatus.setText("Disconnected");
            tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
        }
    }

    private String getLocalIpAddress() {
        if (getContext() == null) return "127.0.0.1";
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getIpAddress() != 0) {
                return Formatter.formatIpAddress(wifiInfo.getIpAddress());
            }
        }
        return "127.0.0.1";
    }

    private void startConnectionOrServer() {
        String portStr = etPort.getText() != null ? etPort.getText().toString().trim() : "";
        if (portStr.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a valid Port number", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = Integer.parseInt(portStr);

        if (isServerMode) {
            startTcpServer(port);
        } else {
            String ipStr = etIpAddress.getText() != null ? etIpAddress.getText().toString().trim() : "";
            if (ipStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter target IP Address", Toast.LENGTH_SHORT).show();
                return;
            }
            connectAsClient(ipStr, port);
        }
    }

    // ==========================================
    // CLIENT MODE IMPLEMENTATION
    // ==========================================

    private void connectAsClient(String ip, int port) {
        tvSocketStatus.setText("Connecting to " + ip + ":" + port + "...");
        tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
        btnConnectSocket.setEnabled(false);

        executor.execute(() -> {
            try {
                Socket socket = new Socket();

                // Check for WifiNetworkSpecifier bound network and bind socket
                Network specifierNetwork = WifiScanFragment.getBoundSpecifierNetwork();
                if (specifierNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    specifierNetwork.bindSocket(socket);
                }

                // Connect socket with timeout
                socket.connect(new InetSocketAddress(ip, port), 6000);

                clientSocket = socket;
                clientWriter = new PrintWriter(socket.getOutputStream(), true);
                clientReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                isConnectedOrRunning = true;

                mainHandler.post(() -> {
                    btnConnectSocket.setEnabled(true);
                    btnConnectSocket.setText("Disconnect");
                    tvSocketStatus.setText("Connected to " + ip + ":" + port);
                    tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connected));
                    addSystemMessage("Connected to server " + ip + ":" + port);
                });

                // Start listening loop
                String line;
                while (isConnectedOrRunning && (line = clientReader.readLine()) != null) {
                    String finalMsg = line;
                    mainHandler.post(() -> addReceivedMessage(finalMsg));
                }

            } catch (Exception e) {
                closeClientSocket();
                mainHandler.post(() -> {
                    btnConnectSocket.setEnabled(true);
                    isConnectedOrRunning = false;
                    btnConnectSocket.setText("Connect");
                    tvSocketStatus.setText("Connection failed: " + e.getLocalizedMessage());
                    tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
                    addSystemMessage("Connection error: " + e.getLocalizedMessage());
                });
            }
        });
    }

    // ==========================================
    // SERVER MODE IMPLEMENTATION
    // ==========================================

    private void startTcpServer(int port) {
        tvSocketStatus.setText("Starting Server on port " + port + "...");
        tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
        btnConnectSocket.setEnabled(false);

        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isConnectedOrRunning = true;

                mainHandler.post(() -> {
                    btnConnectSocket.setEnabled(true);
                    btnConnectSocket.setText("Stop Server");
                    String localIp = getLocalIpAddress();
                    tvSocketStatus.setText("Server Running on " + localIp + ":" + port);
                    tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connected));
                    addSystemMessage("TCP Server started on port " + port + ". Listening for clients...");
                });

                while (isConnectedOrRunning && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();

                    // Handle incoming client on background thread
                    ClientHandler handler = new ClientHandler(socket);
                    connectedClients.add(handler);
                    executor.execute(handler);
                }

            } catch (Exception e) {
                if (isConnectedOrRunning) {
                    mainHandler.post(() -> {
                        btnConnectSocket.setEnabled(true);
                        isConnectedOrRunning = false;
                        btnConnectSocket.setText("Start Server");
                        tvSocketStatus.setText("Server error: " + e.getLocalizedMessage());
                        tvSocketStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
                        addSystemMessage("Server stopped with error: " + e.getLocalizedMessage());
                    });
                }
            }
        });
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private final String clientAddress;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = socket.getRemoteSocketAddress().toString();
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                mainHandler.post(() -> addSystemMessage("Client connected: " + clientAddress));

                String line;
                while (isConnectedOrRunning && (line = reader.readLine()) != null) {
                    String msg = line;
                    mainHandler.post(() -> addReceivedMessage("[" + clientAddress + "]: " + msg));
                    // Broadcast message to all other connected clients
                    broadcastToClients(msg, this);
                }

            } catch (Exception ignored) {
            } finally {
                close();
                connectedClients.remove(this);
                mainHandler.post(() -> addSystemMessage("Client disconnected: " + clientAddress));
            }
        }

        public void send(String msg) {
            if (writer != null) {
                writer.println(msg);
            }
        }

        public void close() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastToClients(String message, ClientHandler excludeHandler) {
        for (ClientHandler client : connectedClients) {
            if (client != excludeHandler) {
                client.send(message);
            }
        }
    }

    // ==========================================
    // MESSAGE SENDING & UI LOGIC
    // ==========================================

    private void sendMessage() {
        String text = etMessageInput.getText() != null ? etMessageInput.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        if (!isConnectedOrRunning) {
            Toast.makeText(getContext(), "Please connect or start server first", Toast.LENGTH_SHORT).show();
            return;
        }

        etMessageInput.setText("");
        addSentMessage(text);

        executor.execute(() -> {
            if (isServerMode) {
                // Server broadcasts to all clients
                broadcastToClients("[Server]: " + text, null);
            } else {
                // Client sends to connected socket
                if (clientWriter != null) {
                    clientWriter.println(text);
                }
            }
        });
    }

    private void addSentMessage(String text) {
        chatAdapter.addMessage(new ChatMessage(text, ChatMessage.SenderType.SENT, getCurrentTime()));
        rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void addReceivedMessage(String text) {
        chatAdapter.addMessage(new ChatMessage(text, ChatMessage.SenderType.RECEIVED, getCurrentTime()));
        rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void addSystemMessage(String text) {
        chatAdapter.addMessage(new ChatMessage(text, ChatMessage.SenderType.SYSTEM, getCurrentTime()));
        rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }

    // ==========================================
    // CLEAN DISCONNECTION LOGIC
    // ==========================================

    private void disconnectOrStop() {
        isConnectedOrRunning = false;
        closeClientSocket();
        closeServerSocket();
        updateModeUi();
    }

    private void closeClientSocket() {
        try {
            if (clientWriter != null) clientWriter.close();
            if (clientReader != null) clientReader.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException ignored) {
        }
        clientSocket = null;
        clientWriter = null;
        clientReader = null;
    }

    private void closeServerSocket() {
        for (ClientHandler client : connectedClients) {
            client.close();
        }
        connectedClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        serverSocket = null;
    }
}