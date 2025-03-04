package net.hearnsoft.gensokyoradio.trd.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.hearnsoft.gensokyoradio.trd.beans.NowPlayingBean;
import net.hearnsoft.gensokyoradio.trd.beans.SocketClientBeans;
import net.hearnsoft.gensokyoradio.trd.model.SongDataModel;
import net.hearnsoft.gensokyoradio.trd.utils.Constants;
import net.hearnsoft.gensokyoradio.trd.utils.NullStringToEmptyAdapterFactory;
import net.hearnsoft.gensokyoradio.trd.utils.ViewModelUtils;
import net.hearnsoft.gensokyoradio.trd.ws.GRWebSocketClient;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class WebSocketService extends Service {
    private static final String TAG = WebSocketService.class.getSimpleName();
    private static WsServiceInterface wsInterface;
    private final ExecutorService signalThreadPool = Executors.newSingleThreadExecutor();
    private GRWebSocketClient wsClient;
    private SharedPreferences.Editor spEditor;
    private Gson gson;
    private SongDataModel songDataModel;
    private Handler toastHandler;
    private URI uri;
    private int clientId;
    private int recheck = 0;

    public static void setCallback(WsServiceInterface dataInterface) {
        wsInterface = dataInterface;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        uri = URI.create(Constants.WS_URL);
        SharedPreferences sharedPreferences = getSharedPreferences("ws", MODE_PRIVATE);
        spEditor = sharedPreferences.edit();
        toastHandler = new Handler(Looper.getMainLooper());
        // 获取全局ViewModel
        songDataModel = ViewModelUtils.getViewModel(getApplication(), SongDataModel.class);
        gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setLenient()
                .serializeNulls()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();
        initWebSocket();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        signalThreadPool.submit(this::initConn);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Service unbind, close WebSocket client
        // Service 解绑，关闭 WebSocket 客户端
        closeWsClient();
        return false;
    }

    private void initWebSocket() {
        wsClient = new GRWebSocketClient(uri) {
            @Override
            public void onMessage(String message) {
                extractData(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                super.onClose(code, reason, remote);
                String reasonLog;
                if (code == 1000 && remote) {
                    reasonLog = "Socket connection closed cleanly,";
                } else {
                    reasonLog = "Socket connection closed unexpectedly, the connection will be retried at a later time.";
                    reConnectSocket();
                }
                Log.d(TAG, reasonLog + " code=" + code + ", reason=" + reason);
            }
        };
        wsClient.setSocketFactory(getDefaultSSLSocketFactory());
    }

    private void reConnectSocket() {
        recheck++;
        postSocketErrorToast(recheck);
        double timeOut;
        switch (recheck) {
            case 1:
                timeOut = Math.floor((Math.random() * 10) + 1);
                break;
            case 2:
                timeOut = Math.floor((Math.random() * 10) + 10);
                break;
            case 3:
                timeOut = Math.floor((Math.random() * 15) + 15);
                break;
            case 4:
                timeOut = Math.floor((Math.random() * 15) + 30);
                break;
            default:
                timeOut = Math.floor((Math.random() * 20) + 45);
                break;
        }
        Timer reConnTimer = new Timer();
        reConnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                initWebSocket();
                initConn();
            }
        }, (long)(timeOut * 1000));
        Log.d(TAG, "will reconnect in " + (timeOut * 1000) + " millisecond later...");
    }

    /**
     * Initiate received data processing
     * 初始化接收到的数据处理
     * @param message
     */
    private void extractData(String message) {
        if (message != null) {
            Log.d(TAG, "socket message:" + message);
            if (isJson(message)) {
                if (message.contains("welcome")) {
                    SocketClientBeans clientBeans = gson.fromJson(message, SocketClientBeans.class);
                    clientId = clientBeans.id;
                    Log.d(TAG, "get Client ID: " + clientId);
                    spEditor.putInt("clientId", clientId);
                    spEditor.apply();
                } else if (message.equals("{\"message\":\"ping\"}")) {
                    Log.d(TAG, "get ping! send pong!");
                    sendPong();
                } else {
                    // Generate bean data
                    genBeanData(message);
                }
            } else if (message.startsWith("Error")) {
                toastHandler.post(() -> Toast.makeText(getApplicationContext(),"ERROR: \n 收到服务器的错误信息: \n" + message, Toast.LENGTH_SHORT).show());
            } else {
                Log.e(TAG, "get invalid json data!");
                toastHandler.post(() -> Toast.makeText(getApplicationContext(),"ERROR: 错误json数据!", Toast.LENGTH_SHORT).show());
            }
        } else {
            Log.e(TAG, "get null data!");
        }
    }

    /**
     * Get default SSLSocketFactory
     * 获取默认 SSLSocketFactory
     */
    private SSLSocketFactory getDefaultSSLSocketFactory() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getDefault();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sslContext.getSocketFactory();
    }

    /**
     * Send initial connection message
     * 发送初始连接消息
     * @params null
     */
    private void initConn() {
        // Connect to WebSocket server and waiting for welcome message
        // 连接到 WebSocket 服务器并等待 welcome 消息
        try {
            wsClient.connectBlocking();
        } catch (InterruptedException e) {
            Log.e(TAG, "webSocket connect time out!");
            e.printStackTrace();
        }
        // If the connection is successful, send the initial connection message
        // 如果连接成功，发送初始连接消息
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(Constants.WS_SESSION_MSG);
        }
    }

    /**
     * Generate bean data
     * 从 json 反序列化生成 bean 数据
     * @param jsonData
     */
    private void genBeanData(String jsonData) {
        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setLenient()
                .serializeNulls()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .registerTypeAdapterFactory(new NullStringToEmptyAdapterFactory<String>())
                .create();
        NowPlayingBean bean = gson.fromJson(jsonData, NowPlayingBean.class);
        wsInterface.beanReceived(bean);

        //genMediaNotification(bean);
        setViewModelData(bean);
        sendUpdateIntent();
    }

    private void sendUpdateIntent() {
        Intent updateIntent = new Intent();
        updateIntent.setAction("net.hearnsoft.gensokyoradio.trd.UPDATE_NOTIFICATION");
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent);
    }

    private void setViewModelData(NowPlayingBean bean) {
        songDataModel.getTitle().postValue(bean.getTitle());
        songDataModel.getArtist().postValue(bean.getArtist());
        songDataModel.getAlbum().postValue(bean.getAlbum());
        songDataModel.getCoverUrl().postValue(bean.getAlbumArt());
        songDataModel.getIsUpdatedInfo().postValue(true);
    }

    /**
     * Check data is JSON data
     * 检查数据是否为 JSON 数据
     * @param jsonData
     * @return boolean isJson
     */
    private boolean isJson(String jsonData) {
        JsonElement jsonElement;
        try {
            jsonElement = JsonParser.parseString(jsonData);
            return jsonElement.isJsonObject();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send pong message
     * 发送 pong 心跳包消息
     * @params null
     */
    private void sendPong() {
        wsClient.send("{\"message\":\"pong\", \"id\":" + clientId + "}");
    }

    /**
     * 显示WebSocket错误断开toast
     */
    private void postSocketErrorToast(int recheck) {
        toastHandler.post(() -> Toast.makeText(getApplicationContext(),"ERROR: Socket连接意外断开！正在重试次数：" + recheck, Toast.LENGTH_SHORT).show());
    }

    /**
     * Close WebSocket client
     * 关闭 WebSocket 客户端
     * @params null
     */
    private void closeWsClient() {
        try {
            if (wsClient != null) {
                wsClient.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            wsClient = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeWsClient();
    }

}
