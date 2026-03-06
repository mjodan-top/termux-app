package com.termux.app.chat;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

/**
 * Background service that manages SSH connections using Termux's ssh binary.
 * Communicates with remote hosts via stdin/stdout of the ssh process,
 * persisting messages through {@link ChatDatabase}.
 */
public class SshChatService extends Service {

    private static final String LOG_TAG = "SshChatService";

    /** SSH connection configuration. */
    public static class SshConfig {
        public String host;
        public int port = 22;
        public String user;

        public SshConfig(String user, String host, int port) {
            this.user = user;
            this.host = host;
            this.port = port;
        }

        public SshConfig(String user, String host) {
            this(user, host, 22);
        }

        /** Default constructor for use by parse(). */
        public SshConfig() {
        }

        /**
         * Parse an SSH connection string in the format: user@host or user@host:port.
         * If no user is specified, defaults to "root". If no port, defaults to 22.
         *
         * @param input the connection string to parse
         * @return a valid SshConfig, or null if parsing fails
         */
        public static SshConfig parse(String input) {
            if (input == null || input.trim().isEmpty()) return null;
            input = input.trim();
            SshConfig config = new SshConfig();
            try {
                String hostPart;
                if (input.contains("@")) {
                    config.user = input.substring(0, input.indexOf("@"));
                    hostPart = input.substring(input.indexOf("@") + 1);
                } else {
                    config.user = "root";
                    hostPart = input;
                }
                if (hostPart.contains(":")) {
                    config.host = hostPart.substring(0, hostPart.indexOf(":"));
                    config.port = Integer.parseInt(hostPart.substring(hostPart.indexOf(":") + 1));
                } else {
                    config.host = hostPart;
                    config.port = 22;
                }
                if (config.host.isEmpty()) return null;
                return config;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Binder for local binding. */
    public class LocalBinder extends Binder {
        public SshChatService getService() {
            return SshChatService.this;
        }
    }

    /** Callback interface for SSH events. */
    public interface SshChatListener {
        void onMessageReceived(ChatMessage message);
        void onConnectionStateChanged(boolean connected, String sessionId);
        void onError(String error);
    }

    private final IBinder mBinder = new LocalBinder();

    private boolean mIsConnected = false;
    private Process mSshProcess;
    private BufferedReader mProcessReader;
    private BufferedWriter mProcessWriter;
    private Thread mReaderThread;
    private String mCurrentSessionId;
    private ChatDatabase mDatabase;
    private Handler mMainHandler;
    private SshChatListener mListener;

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = ChatDatabase.getInstance(this);
        mMainHandler = new Handler(Looper.getMainLooper());
        Log.i(LOG_TAG, "SshChatService created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "SshChatService destroying");
        disconnect();
        super.onDestroy();
    }

    /**
     * Set the listener for SSH events. Pass null to remove.
     */
    public void setListener(SshChatListener listener) {
        mListener = listener;
    }

    /**
     * Connect to a remote host via SSH.
     *
     * @param config SSH connection parameters
     */
    public void connect(SshConfig config) {
        if (mIsConnected) {
            disconnect();
        }

        mCurrentSessionId = config.user + "@" + config.host + ":" + config.port;
        String sshPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/ssh";

        String[] cmd = {
            sshPath,
            "-p", String.valueOf(config.port),
            "-o", "StrictHostKeyChecking=no",
            "-o", "BatchMode=yes",
            config.user + "@" + config.host
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            // Set environment for Termux
            Map<String, String> env = pb.environment();
            env.put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":" + System.getenv("PATH"));
            env.put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
            env.put("TERM", "dumb");
            env.put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);

            mSshProcess = pb.start();
            mProcessReader = new BufferedReader(new InputStreamReader(mSshProcess.getInputStream()));
            mProcessWriter = new BufferedWriter(new OutputStreamWriter(mSshProcess.getOutputStream()));

            mIsConnected = true;
            startReaderThread();

            // Insert system message
            ChatMessage sysMsg = ChatMessage.createSystemMessage(mCurrentSessionId,
                "Connected to " + mCurrentSessionId);
            mDatabase.insertMessage(sysMsg);

            notifyConnectionStateChanged(true, mCurrentSessionId);
            Log.i(LOG_TAG, "Connected to " + mCurrentSessionId);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to start SSH process", e);
            mIsConnected = false;
            mCurrentSessionId = null;
            notifyError("Failed to connect: " + e.getMessage());
        }
    }

    /**
     * Send a message (line of text) through the SSH connection.
     *
     * @param text the text to send
     */
    public void sendMessage(String text) {
        if (!mIsConnected || mProcessWriter == null) {
            notifyError("Not connected");
            return;
        }

        try {
            mProcessWriter.write(text);
            mProcessWriter.newLine();
            mProcessWriter.flush();

            ChatMessage msg = ChatMessage.createSentMessage(mCurrentSessionId, text, mCurrentSessionId);
            mDatabase.insertMessage(msg);

            // Notify listener on main thread
            if (mListener != null) {
                mMainHandler.post(() -> {
                    if (mListener != null) {
                        mListener.onMessageReceived(msg);
                    }
                });
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to send message", e);
            notifyError("Send failed: " + e.getMessage());
        }
    }

    /**
     * Disconnect from the current SSH session.
     */
    public void disconnect() {
        if (mReaderThread != null) {
            mReaderThread.interrupt();
            mReaderThread = null;
        }

        if (mProcessWriter != null) {
            try {
                mProcessWriter.close();
            } catch (IOException e) {
                Log.w(LOG_TAG, "Error closing writer", e);
            }
            mProcessWriter = null;
        }

        if (mProcessReader != null) {
            try {
                mProcessReader.close();
            } catch (IOException e) {
                Log.w(LOG_TAG, "Error closing reader", e);
            }
            mProcessReader = null;
        }

        if (mSshProcess != null) {
            mSshProcess.destroy();
            mSshProcess = null;
        }

        if (mIsConnected && mCurrentSessionId != null) {
            ChatMessage sysMsg = ChatMessage.createSystemMessage(mCurrentSessionId, "Disconnected");
            mDatabase.insertMessage(sysMsg);
            notifyConnectionStateChanged(false, mCurrentSessionId);
            Log.i(LOG_TAG, "Disconnected from " + mCurrentSessionId);
        }

        mIsConnected = false;
    }

    /**
     * @return true if currently connected to an SSH host
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * @return the current session ID (user@host:port), or null if not connected
     */
    public String getCurrentSessionId() {
        return mCurrentSessionId;
    }

    /**
     * Get recent chat history for the current session.
     *
     * @param limit maximum number of messages to return
     * @return list of recent messages in ascending timestamp order
     */
    public List<ChatMessage> getHistory(int limit) {
        if (mCurrentSessionId == null) {
            return java.util.Collections.emptyList();
        }
        return mDatabase.getRecentMessages(mCurrentSessionId, limit);
    }

    /**
     * Get recent chat history for a specific session.
     *
     * @param sessionId the session identifier
     * @param limit     maximum number of messages to return
     * @return list of recent messages in ascending timestamp order
     */
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        return mDatabase.getRecentMessages(sessionId, limit);
    }

    private void startReaderThread() {
        mReaderThread = new Thread(() -> {
            try {
                String line;
                while (!Thread.currentThread().isInterrupted() && (line = mProcessReader.readLine()) != null) {
                    final String content = line;
                    ChatMessage msg = ChatMessage.createReceivedMessage(mCurrentSessionId, content, mCurrentSessionId);
                    mDatabase.insertMessage(msg);

                    if (mListener != null) {
                        mMainHandler.post(() -> {
                            if (mListener != null) {
                                mListener.onMessageReceived(msg);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(LOG_TAG, "Reader thread error", e);
                    notifyError("Connection lost: " + e.getMessage());
                }
            } finally {
                if (mIsConnected) {
                    mIsConnected = false;
                    mMainHandler.post(() -> {
                        if (mCurrentSessionId != null) {
                            ChatMessage sysMsg = ChatMessage.createSystemMessage(mCurrentSessionId, "Connection closed");
                            mDatabase.insertMessage(sysMsg);
                            notifyConnectionStateChanged(false, mCurrentSessionId);
                        }
                    });
                }
            }
        }, "SshChatReader");
        mReaderThread.setDaemon(true);
        mReaderThread.start();
    }

    private void notifyConnectionStateChanged(boolean connected, String sessionId) {
        if (mListener != null) {
            mMainHandler.post(() -> {
                if (mListener != null) {
                    mListener.onConnectionStateChanged(connected, sessionId);
                }
            });
        }
    }

    private void notifyError(String error) {
        Log.e(LOG_TAG, error);
        if (mListener != null) {
            mMainHandler.post(() -> {
                if (mListener != null) {
                    mListener.onError(error);
                }
            });
        }
    }
}
