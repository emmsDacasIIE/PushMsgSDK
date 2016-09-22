package cn.dacas.pushmessagesdk;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.util.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.sauronsoftware.base64.Base64;

/**
 * Created by Sun RX on 2016-9-7.
 * PushMsg Manager
 * 1. Constructor
 * 2. registerPush
 * and then it will work
 */
public class PushMsgManager {

     private String TAG = "MQTT";
     static private MqttAndroidClient mqttAndroidClient;
     static private SharedPreferences sharedPreferences = null;
    //通知主题队列
     static private List<String> notifyTopicLists = new ArrayList<>();
    //普通消息主题队列
     static private List<String> msgTopicLists = new ArrayList<>();
     private String regServerUrl ="";//注册服务器
     private String pushServerUrl = "";//消息推送服务器
     private String clientId, clientSecret;
     private String regId = "";//注册后，由Server返回
     private RequestQueue mQueue = null;
     private Context context;
     private HashMap<String,String> headers = new HashMap<>();

     static private Class notificationToActivity = null;
     static private int notificationIndex = 402;
     private int icon;

    public interface CommCodeType{
        int NET_GetRedId = 1;
        int NET_GetAliase = 2;
        int NET_Add= 3;
        int NET_Delete = 4;
        int NET_GetAccounts = 5;
        int NET_GetTopics = 6;
        int NET_AddAliases= 7;

        int TO_GET_TOPICS = 10;
        int TO_STARTWORK = 11;
    }

    private Handler handler = new Handler(){
        public void handleMessage(Message msg)
        {
            switch (msg.what){
                //reg_id has been get,and then it goes to generate topics
                case CommCodeType.TO_GET_TOPICS:
                    addTopicsIntoList();
                    sendHandleMsg(CommCodeType.TO_STARTWORK,"");
                    break;
                //to start work
                case CommCodeType.TO_STARTWORK:
                    try {
                        startWork(pushServerUrl);
                    } catch (Exception e) {
                        sendMsgBroadcast(ActionType.err,e.getMessage());
                    }
                    break;
                case CommCodeType.NET_AddAliases:
                    subscribeToTopicLists(getNewMList(msg.what,parseStringToList(msg.obj.toString())));
                    break;
                default:
                    //Toast.makeText(context,msg.toString(),Toast.LENGTH_SHORT).show();
                    Log.d(TAG, msg.obj.toString());
                    break;
            }
        }
    };

    /**
     * subscribe new topics and save them in Lists
     * @param type topic type
     */
    private void onGetNewTopic(int type, String string){
        List<String> mList = getNewMList(type,parseStringToList(string));
    }
    private void addTopicsIntoList() {
        //add regId topics
        notifyTopicLists.add(generateSubMsgTopic(CommCodeType.NET_GetRedId,regId,0));
        msgTopicLists.add(generateSubMsgTopic(CommCodeType.NET_GetRedId,regId,1));
        addLocalKeywordsToTopicsList(CommCodeType.NET_GetAliase);
        addLocalKeywordsToTopicsList(CommCodeType.NET_GetAccounts);
        addLocalKeywordsToTopicsList(CommCodeType.NET_GetTopics);
    }

    private void addLocalKeywordsToTopicsList(int type){
        if(type!=CommCodeType.NET_GetAliase&&type!=CommCodeType.NET_GetTopics&&type!=CommCodeType.NET_GetAccounts)
            return;
        List<String> accountsList = getLocalArray(type);
        if(accountsList.size()==0)
            getJsonArrayFormServer(type);
        for (String topic : accountsList) {
            notifyTopicLists.add(generateSubMsgTopic(type,topic,0));
            msgTopicLists.add(generateSubMsgTopic(type,topic,1));
        }
    }

    private  List<String> getNewMList(int type, List<String> accountsList){
        List<String> mlist = new ArrayList<>();
        for (String topic : accountsList) {
            mlist.add(generateSubMsgTopic(type,topic,1));
        }
        return mlist;
    }
    private  List<String> getNewNList(int type, List<String> accountsList){
        List<String> mlist = new ArrayList<>();
        for (String topic : accountsList) {
            mlist.add(generateSubMsgTopic(type,topic,0));
        }
        return mlist;
    }
    /**
     * 发送内类消息
     * @param code 消息码 CommCodeType
     * @param msg 辅助的消息内容
     */
    private void sendHandleMsg(int code, String msg){
        Message message = new Message();
        message.obj = msg;
        message.what = code;
        handler.sendMessage(message);
    }

    /**
     * Constructor
     * @param context 环境上线文
     * @param pushServer 消息推送服务器地址
     */
    public PushMsgManager(Context context,
                          String pushServer) {
        this.context = context;
        pushServerUrl = pushServer;
        mQueue = Volley.newRequestQueue(context);
        notificationToActivity = context.getClass();
        sharedPreferences = context.getSharedPreferences("data",Context.MODE_PRIVATE);
        icon = getNotificationIcon();
    }

    /**
     * 本地保存String值
     * @param key 键
     * @param value 值
     */
    private void putStringData(String key, String value){
        if(context == null)
            return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key,value);
        editor.commit();
    }

    /**
     * 获得已经保存的值
     * @param key 键
     * @param defaultValue 默认值
     * @return 该键存在则返回对应的值，如果不存在就返回默认值
     */
    private String getStringData(String key, String defaultValue){
        return sharedPreferences.getString(key,defaultValue);
    }

    /**
     * 设置现有通知数量
     * @param counter 要设置的数值
     */
    static void setNotifyMsgCounter(int counter) {
        BaseMessageReceiver.setMsgCounter(counter);
    }

    /**
     * 广播Action类型
     */
    public interface ActionType{
        String preString = "cn.ac.iie.emms.PushMsg.";
        String err = preString+"ERROR";
        String receive_msg = preString + "RECEIVE_MESSAGE";
        String receive_notification = preString+"RECEIVE_NOTIFICATION";
        String clear_msg_notification = preString + "CLEAR_MSG_NOTIFICATION";
    }


    /**
     * add topic to subscription Topic list
     * @param topics 主题
     */
     public void addTopic(List<String> topics){
        if (topics.size() == 0)
            return;
        for (String topic:topics) {
            if(!notifyTopicLists.contains(topic)){
                notifyTopicLists.add(topic);
                addToLog("add topic = "+topic);
            }
        }
    }

    public void subToTopic(String Topic) {
        try {
            //设置订阅动作监听器
            addToLog("Sub to "+Topic);
            mqttAndroidClient.subscribe(Topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addToLog("Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToLog("Failed to subscribe");
                    subscribeToTopicLists(notifyTopicLists);
                }
            });

            //设置内容监听器
            mqttAndroidClient.subscribe(Topic, 0 ,new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // message Arrived!订阅该主题，该主题有消息到达时触发该函数
                    addToLog(topic+" messageArrived: "+new String(message.getPayload()));
                    if(notifyTopicLists.contains(topic))
                        sendMsgBroadcast(ActionType.receive_notification,message);
                    else
                        sendMsgBroadcast(ActionType.receive_msg, message);
                }
            });
        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void subscribeToTopicLists(List<String> topicLists){
        if (topicLists.size() == 0)
            return;
        for (String topic:topicLists) {
            subToTopic(topic);
        }
    }

    public void registerPush(String url, String clientId, String clientSecret) throws Exception {
        //生成 http header
        this.clientId  = clientId;
        this.clientSecret = clientSecret;
        String authorizationKey = Base64.encode(this.clientId+":"+ this.clientSecret);
        headers.put("Authorization", "Basic " + authorizationKey);
        headers.put("Content-Type", "application/json; charset=UTF-8");

        /**实验，应删除**/
        putStringData("reg_id","");
        //判断reg_id 是否已经存在
        regId = getStringData("reg_id","");
        Log.d(TAG, "reg_id:"+regId);
         if(!regId.equals(""))//已经注册过了
         {   //发出局部消息，已经得到reg_id,下一步生成topic；
             sendHandleMsg(CommCodeType.TO_GET_TOPICS,"");
             return;
         }

        //没有注册，则开始注册
         this.regServerUrl = url;
         this.clientId = clientId;
         this.clientSecret = clientSecret;
         try{
             //相服务器注册当前应用
             //上传IMEI
             JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST,
                     regServerUrl,//"?uuid="+getIMEI(context),
                     redIdListener, errorListener){
                 @Override
                 public Map<String, String> getHeaders() throws AuthFailureError {
                     return headers;
                 }

                 @Override
                 public byte[] getBody() {
                     JSONObject object = new JSONObject();
                     try {
                         object.put("uuid",getIMEI(context));
                     } catch (JSONException e) {
                         e.printStackTrace();
                     }
                     return object.toString().getBytes();
                 }
             };
             mQueue.add(jsonObjectRequest);
         }catch (Exception e){
             e.printStackTrace();
         }
    }

    public void startWork(String url) throws Exception {
        pushServerUrl = url;
        mqttAndroidClient = new MqttAndroidClient(context, pushServerUrl, regId);
        mqttAndroidClient.setCallback(callbackExtended);
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //尝试连接
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopicLists(notifyTopicLists);
                    subscribeToTopicLists(msgTopicLists);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToLog("Failed to connect to: " + pushServerUrl);
                    sendMsgBroadcast(ActionType.err, exception.getMessage());
                }
            });
        } catch (MqttException ex){
            throw new Exception(ex.toString());
        }
    }

    private MqttCallbackExtended callbackExtended = new MqttCallbackExtended() {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {//自动重连的结果，需要重新订阅主题
                // Because Clean Session is true, we need to re-subscribe
                subscribeToTopicLists(notifyTopicLists);
                subscribeToTopicLists(msgTopicLists);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            sendMsgBroadcast(ActionType.err,cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {

        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {

        }
    };

    private void addToLog(String mainText){
        String TAG = "MQTT";
        Log.d(TAG, mainText);
    }

    /**
     * 发出APP中广播
     * @param action ActionType
     * @param message 接收到的消息
     */
     private void sendMsgBroadcast(String action, MqttMessage message){
        Intent intent = new Intent(action);
        intent.putExtra("MsgData",new String(message.getPayload()));
         if(action == ActionType.clear_msg_notification) {
             intent.putExtra("ICON",icon);
         }
        if (context!=null)
            context.sendBroadcast(intent);
    }
    /**
     * 发出APP中广播
     * @param action ActionType
     * @param errMsg 错误说明
     */
     private void sendMsgBroadcast(String action, String errMsg){
        Intent intent = new Intent(action);
        intent.putExtra("MsgData",errMsg);
        if (context!=null)
            context.sendBroadcast(intent);
    }

    /**
     * 向某个主题发送消息
     * @param topic 主题
     * @param msg 消息
     */
    public void publishToTopic(String topic, String msg) {
        try {
            MqttMessage message = new MqttMessage();
            //设置消息内容
            message.setPayload(msg.getBytes());
            //发送消息
            mqttAndroidClient.publish(topic, message);
            addToLog(topic + " Message Published");
            if(!mqttAndroidClient.isConnected()){
                addToLog(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static public void cancelNotification(Context context){
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationIndex);
        setNotifyMsgCounter(0);
    }

    public static void refleshMsgNotification(Context context,Intent intent){
        Boolean isFromMsg = intent.getBooleanExtra("FromMsg",false);
        if(isFromMsg && notificationToActivity !=null)
            cancelNotification(context);
    }

    private int getNotificationIcon(){
        int idFlag = context.getResources().getIdentifier(
                "push_notification", "drawable", context.getPackageName());
        if (idFlag != 0) {
            return idFlag;
        }
        else {
            return context.getApplicationInfo().icon ;
        }
    }

    //负责接收reg_id
    private Response.Listener<JSONObject> redIdListener = new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            try {
                //提取redId
                regId = response.getString("reg_id");
                sendHandleMsg(CommCodeType.TO_GET_TOPICS,"");

            } catch (JSONException e) {
                sendMsgBroadcast(ActionType.err,e.getMessage());
            }
            Log.d(TAG, "onResponse: reg_id:"+ regId);
            //存储
            putStringData("reg_id", regId);
        }
    };

    private Response.Listener<JSONArray> aliasesListener = new Response.Listener<JSONArray>() {
        @Override
        public void onResponse(JSONArray response) {
            if (response.length()==0)//response without data
                return;
            String aliases = response.toString();
            Log.d(TAG, "get aliases:"+aliases);
            //存储
            putStringData("aliases",aliases);
            sendHandleMsg(CommCodeType.NET_GetAliase,aliases);
        }
    };

    private Response.Listener<JSONArray> accountListener = new Response.Listener<JSONArray>() {
        @Override
        public void onResponse(JSONArray response) {
            if (response.length()==0)//response without data
                return;
            String account = response.toString();
            Log.d(TAG, "get account:"+account);
            //存储
            putStringData("user_accounts",account);
            sendHandleMsg(CommCodeType.NET_GetAccounts,account);
        }
    };

    private Response.Listener<JSONArray> topicListener = new Response.Listener<JSONArray>() {
        @Override
        public void onResponse(JSONArray response) {
            if (response.length()==0)//response without data
                return;
            String topic = response.toString();
            Log.d(TAG, "get topic:"+topic);
            //存储
            putStringData("topics",topic);
            sendHandleMsg(CommCodeType.NET_GetTopics,topic);
        }
    };

    private Response.ErrorListener errorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e(TAG, error.toString(), error);
            //发动广播，提示错误
            sendMsgBroadcast(ActionType.err,error.toString());
        }
    };

    private String getIMEI(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = null;
        try{
            imei = tm.getDeviceId();
        }catch (RuntimeException e){
            e.printStackTrace();
        }
        if (imei == null) {
            imei = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return imei;
    }

    public void getJsonArrayFormServer(int type){
        Response.Listener<JSONArray> listener;
        String url = regServerUrl+"/"+ regId;
        switch (type){
            case CommCodeType.NET_GetAliase:
                listener = aliasesListener;
                url = url+"/aliases";
                break;
            case CommCodeType.NET_GetAccounts:
                listener = accountListener;
                url = url+"/user_accounts";
                break;
            case CommCodeType.NET_GetTopics:
                listener = topicListener;
                url = url+"/topics";
                break;
            default:
                return;
        }
        try{
            JsonArrayRequest arrayRequest = new JsonArrayRequest(url,
                    listener, errorListener) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    return headers;
                }
            };
            mQueue.add(arrayRequest);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 向服务器添加JsonArray数据
     * @param comm Method POST/DELETE
     * @param type 0:alias ; 1:account; 2:news topic
     * @param jsonArray 数据
     */
    public void sendJsonArrayToServer(int comm,int type, final JSONArray jsonArray){
        String url = regServerUrl+"/"+ regId;
        final int method = comm;
        int commType;
        if(method!= Request.Method.POST&&method!= Request.Method.DELETE)
            return;
        final String tag ;
        switch (type){
            case 0:
                tag = "/aliases";
                if(method == Request.Method.POST)
                    commType = CommCodeType.NET_AddAliases;
                else
                    commType = CommCodeType.NET_Delete;
                break;
            case 1:
                tag = "/user_accounts";
                if(method == Request.Method.POST)
                    commType = CommCodeType.NET_Add;
                else
                    commType = CommCodeType.NET_Delete;
                break;
            case 2:
                tag = "/topics";
                if(method == Request.Method.POST)
                    commType = CommCodeType.NET_Add;
                else
                    commType = CommCodeType.NET_Delete;
                break;
            default:
                return;
        }
        MyStringRequest request = new MyStringRequest(method,
                url+tag, commType, headers, new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                if(s.equals("Err"))
                    sendMsgBroadcast(ActionType.err,
                            ((method== Request.Method.POST)? "ADD": "DELETE")+" Err!"+tag);
                else
                    sendHandleMsg(((method== Request.Method.POST)? CommCodeType.NET_Add: CommCodeType.NET_Delete),
                            ((method== Request.Method.POST)? "ADD": "DELETE")+" Ok!"+tag);
            }
        },errorListener){
            @Override
            public byte[] getBody() throws AuthFailureError {
                return jsonArray.toString().getBytes();
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        mQueue.add(request);
    }

    public static List<String> getNotifyTopicLists() {
        return notifyTopicLists;
    }

    public static List<String> getMsgTopicLists() {
        return msgTopicLists;
    }

    public static Class getNotificationToActivity() {
        return notificationToActivity;
    }

    private List<String> getLocalArray(int type){
        String tag="aliases";
        switch (type){
            case CommCodeType.NET_GetAliase:
                tag="aliases";
                break;
            case CommCodeType.NET_GetAccounts:
                tag="user_accounts";
                break;
            case CommCodeType.NET_GetTopics:
                tag="topics";
                break;
            default:
                break;
        }
        String goalString = getStringData(tag,"");
        return parseStringToList(goalString);
    }

    /**
     * pares a JSONArray String to a list whose elements are JSONObjects in JSONArray;
     * @param string JSONArray String
     * @return a list whose elements are JSONObjects in JSONArray
     */
    private static List<String> parseStringToList(String string){
        List<String> list = new ArrayList<>();
        if(string.equals(""))
            return list;
        try {
            JSONArray jsonArray = new JSONArray(string);
            for (int i = 0; i <jsonArray.length() ; i++) {
                list.add(jsonArray.getString(i));
            }
        } finally {
            return list;
        }
    }

    /**
     * generate topics to subscribe;
     * @param commend preString in the topic
     * @param keyword keyword in the topic
     * @param type the type of topic: 0, notifications ; 1, messages
     * @return topics to subscribe
     */
    private String generateSubMsgTopic(int commend,String keyword ,int type){
        String result = "";
        switch (commend){
            case CommCodeType.NET_GetRedId:
                if( type == 0)
                    result = clientId+"/"+ keyword +"/notifications";
                else if(type==1)
                    result = clientId+"/"+ keyword +"/messages";
                break;
            case CommCodeType.NET_GetAliase:
                if(type == 0)
                    result = clientId+"/aliases/"+ keyword +"/notifications";
                else if(type==1)
                    result = clientId+"/aliases/"+ keyword +"/messages";
                break;
            case CommCodeType.NET_GetAccounts:
                if(type == 0)
                    result = clientId+"/user_accounts/"+ keyword +"/notifications";
                else if(type==1)
                    result = clientId+"/user_accounts/"+ keyword +"/messages";
                break;
            case CommCodeType.NET_GetTopics:
                if(type == 0)
                    result = clientId+"/topics/"+ keyword +"/notifications";
                else if(type==1)
                    result = clientId+"/topics/"+ keyword +"/messages";
                break;
            default:
                return "";
        }
        return result;
    }

    /**
     * test method
     * @param s client id
     */
    void setClientId(String s){
        this.clientId = s;
    }

    /**
     * test method
     * @param regId reg_id
     */
    void setRegId(String regId){
        this.regId = regId;
    }

    /**
     * test method
     */
    public PushMsgManager(){}
}
