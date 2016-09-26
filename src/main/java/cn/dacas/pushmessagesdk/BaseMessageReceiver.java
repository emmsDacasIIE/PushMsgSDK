package cn.dacas.pushmessagesdk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Sun RX on 2016-9-7.
 * This is a abstract Class based on BroadcastReceiver.
 * abstract methods onReceive() and onError() should be implemented.
 */
public abstract class BaseMessageReceiver extends BroadcastReceiver{
    private static int notificationIndex = 402;
    private static int msgCounter=0;
    private int icon;
    private Class notificationToActivity;

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    static public int getNotificationIndex() {
        return notificationIndex;
    }

    static public void setNotificationIndex(int notificationIndex) {
        BaseMessageReceiver.notificationIndex = notificationIndex;
    }

    public Class getNotificationToActivity() {
        return notificationToActivity;
    }

    public void setNotificationToActivity(Class notificationToActivity) {
        this.notificationToActivity = notificationToActivity;
    }

    public static int getMsgCounter() {
        return msgCounter;
    }

    public static void setMsgCounter(int n){
        msgCounter = n;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String msgData = intent.getStringExtra("MsgData");
        switch (action){
            case PushMsgManager.ActionType.err:
                onError(context,msgData);
                break;
            case PushMsgManager.ActionType.receive_msg:
                onMsgArrived(context,msgData);
                break;
            case PushMsgManager.ActionType.receive_notification:
                onMsgArrived(context,msgData);
                icon = intent.getIntExtra("ICON",0);
                notificationToActivity = PushMsgManager.getNotificationToActivity();
                onNotificationArrived(context,msgData);
                break;
            case PushMsgManager.ActionType.clear_msg_notification:
                PushMsgManager.cancelNotification(context);
                break;
        }
    }

    /**
     * to deal with error messages.
     * When exceptions are thrown during messages pushing. this method will be called.
     * @param context Context
     * @param msg err messages
     */
    protected abstract void onError(Context context, String msg);

    /**
     * to deal with pushed messages.
     * When messages pushed by PushServer have arrived. this method will be called.
     * @param context Context
     * @param msg pushed messages
     */
    protected abstract void onMsgArrived(Context context, String msg);

    /**
     * to deal with Notifications.
     * When exceptions are thrown during messages pushing. this method will be called.
     * @param context Context
     * @param msg err messages
     */
    protected void onNotificationArrived(Context context,String msg){
        int notifyICON = getIcon();
        Class toActivity = getNotificationToActivity();
        String msgString = msg;
        String title="消息推送", content;
        try {
            JSONObject jsonMsg = new JSONObject(msgString);
            title = jsonMsg.getString("title");
            content = jsonMsg.getString("content");

        } catch (JSONException e) {
            content = msgString;
        }

        if (context != null) {
            // 创建一个NotificationManager的引用
            NotificationManager notificationManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            Intent notificationIntent = new Intent(
                    context.getApplicationContext(),
                    //点击该通知后要跳转的Activity
                    toActivity);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            notificationIntent.putExtra("FromMsg", true);
            notificationIntent.putExtra("Data",msgString);
            msgCounter++;
            if (msgCounter > 1) {
                content = "您有" + String.valueOf(msgCounter) + "条未读消息。";
                title="消息推送";
            }

            PendingIntent contentItent = PendingIntent.getActivity(
                    context.getApplicationContext(), 0,
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);//不加最后一个Flag 数据不会更新的；

            Notification.Builder notificationBuilder = new Notification.Builder(
                    context);
            notificationBuilder
                    .setSmallIcon(notifyICON)
                    .setTicker("推送消息")
                    .setWhen(System.currentTimeMillis())
                    .setDefaults(
                            Notification.DEFAULT_VIBRATE
                                    | Notification.DEFAULT_SOUND)
                    .setContentTitle(title).setContentText(content)
                    .setContentIntent(contentItent);

            Notification notification = notificationBuilder.build();
            notification.flags |= Notification.FLAG_ONGOING_EVENT; // 将此通知放到通知栏的"Ongoing"即"正在运行"组中
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notificationManager.notify(notificationIndex, notification);
        }
    }
}
