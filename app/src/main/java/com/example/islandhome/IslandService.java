package com.example.islandhome;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.provider.Settings;import android.view.*;import android.widget.*;
public class IslandService extends Service {
 public static IslandService instance; WindowManager wm; TextView pill; WindowManager.LayoutParams lp; boolean expanded;String detail="Island Home active";PendingIntent action;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("island","Island overlay service",NotificationManager.IMPORTANCE_LOW));PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);startForeground(1,new Notification.Builder(this,"island").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Island is running").setContentText("Open Island Home to turn it off").setContentIntent(pi).build());
 if(!Settings.canDrawOverlays(this)){stopSelf();return;}wm=getSystemService(WindowManager.class);pill=new TextView(this);pill.setTextColor(Color.WHITE);pill.setGravity(Gravity.CENTER);pill.setTextSize(12);pill.setPadding(dp(12),dp(4),dp(12),dp(4));GradientDrawable b=new GradientDrawable();b.setColor(Color.BLACK);b.setCornerRadius(dp(30));pill.setBackground(b);pill.setElevation(dp(8));
 lp=new WindowManager.LayoutParams(dp(160),dp(38),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=dp(4);
 pill.setText("Island Home");pill.setOnClickListener(v->{expanded=!expanded;lp.width=dp(expanded?300:160);lp.height=dp(expanded?100:38);pill.setMaxLines(expanded?4:1);pill.setText(expanded?detail:"Island Home");wm.updateViewLayout(pill,lp);});pill.setOnLongClickListener(v->{if(action!=null)try{action.send();}catch(Exception e){}return true;});
 try{wm.addView(pill,lp);instance=this;}catch(Exception e){stopSelf();}}
 public void update(String text,PendingIntent intent){new Handler(getMainLooper()).post(()->{detail=text;action=intent;if(pill!=null)pill.setText(expanded?detail:"●  "+text);});}
 public int onStartCommand(Intent i,int f,int id){return START_NOT_STICKY;} public IBinder onBind(Intent i){return null;}
 public void onDestroy(){instance=null;if(wm!=null&&pill!=null)try{wm.removeView(pill);}catch(Exception e){}super.onDestroy();}
  
}
