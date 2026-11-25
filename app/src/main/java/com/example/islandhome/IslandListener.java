package com.example.islandhome;
import android.service.notification.*;import android.app.*;import android.os.Bundle;
public class IslandListener extends NotificationListenerService {
 public void onNotificationPosted(StatusBarNotification sbn){if(sbn.getPackageName().equals(getPackageName()))return;IslandService s=IslandService.instance;if(s==null)return;Notification n=sbn.getNotification();Bundle e=n.extras;CharSequence title=e.getCharSequence(Notification.EXTRA_TITLE),text=e.getCharSequence(Notification.EXTRA_TEXT);s.update((title==null?sbn.getPackageName():title.toString())+(text==null?"":" — "+text),n.contentIntent);}
 public void onNotificationRemoved(StatusBarNotification sbn){if(IslandService.instance!=null&&!sbn.getPackageName().equals(getPackageName()))IslandService.instance.update("Island Home active",null);}
  
}
