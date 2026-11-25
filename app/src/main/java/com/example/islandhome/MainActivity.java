package com.example.islandhome;
import android.app.*; import android.app.role.RoleManager; import android.os.*; import android.content.*; import android.content.pm.*; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.net.Uri; import android.provider.Settings; import android.text.*; import android.view.*; import android.widget.*; import java.util.*;
public class MainActivity extends Activity {
 LinearLayout root, gridHost; GridLayout grid; EditText search; java.util.List<ResolveInfo> apps;
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 GradientDrawable bg(int a,int b,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b}); d.setCornerRadius(dp(radius)); return d;}
 public void onCreate(Bundle state){super.onCreate(state); getWindow().setStatusBarColor(Color.rgb(29,34,72)); getWindow().setNavigationBarColor(Color.rgb(30,28,65));
 root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(10),dp(12),dp(8));root.setBackground(bg(0xff1d2248,0xff71518d,0));setContentView(root);
 TextView title=new TextView(this); title.setText("Island Home"); title.setTextSize(25);title.setTextColor(-1);root.addView(title);
 LinearLayout bar=new LinearLayout(this); root.addView(bar); button(bar,"Setup",()->setup());button(bar,"Island on",()->startIsland());button(bar,"Off",()->stopService(new Intent(this,IslandService.class)));
 search=new EditText(this);search.setSingleLine(true);search.setHint("Search apps");search.setTextColor(-1);search.setHintTextColor(0xffdddddd);root.addView(search);
 ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));grid=new GridLayout(this);scroll.addView(grid);
 LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setPadding(0,dp(8),0,dp(8));dock.setBackground(bg(0x55ffffff,0x22ffffff,28));root.addView(dock);
 button(dock,"Phone",()->open(new Intent(Intent.ACTION_DIAL)));button(dock,"Web",()->open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com"))));button(dock,"Camera",()->open(new Intent("android.media.action.STILL_IMAGE_CAMERA")));
 Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);apps=getPackageManager().queryIntentActivities(q,0);Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render(s.toString());}public void afterTextChanged(Editable e){}});render("");
 }
 void button(LinearLayout row,String name,Runnable r){Button b=new Button(this);b.setText(name);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));b.setOnClickListener(v->r.run());}
 void render(String filter){grid.removeAllViews();int cols=Math.max(4,Math.min(8,getResources().getConfiguration().screenWidthDp/85));grid.setColumnCount(cols);
 for(ResolveInfo info:apps){String label=info.loadLabel(getPackageManager()).toString();if(!label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))continue;
 LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(3),dp(10),dp(3),dp(8));GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=(getResources().getDisplayMetrics().widthPixels-dp(24))/cols;p.height=dp(101);grid.addView(cell,p);
 ImageView icon=new ImageView(this);icon.setImageDrawable(info.loadIcon(getPackageManager()));icon.setBackground(bg(0x33ffffff,0x11ffffff,14));icon.setClipToOutline(true);cell.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
 TextView text=new TextView(this);text.setText(label);text.setTextColor(-1);text.setTextSize(11);text.setGravity(Gravity.CENTER);text.setMaxLines(2);cell.addView(text);
 cell.setOnClickListener(v->open(new Intent().setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
 }}
 void open(Intent i){try{startActivity(i);}catch(Exception e){Toast.makeText(this,"No compatible app found",0).show();}}
 void setup(){new AlertDialog.Builder(this).setTitle("Setup permissions").setItems(new String[]{"Set default home app","Allow display over other apps","Enable notification access","Allow service notifications"},(d,n)->{if(n==0){if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_HOME)){startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),10);return;}}open(new Intent(Settings.ACTION_HOME_SETTINGS));}if(n==1)open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));if(n==2)open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));if(n==3&&Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},11);}).show();}
 void startIsland(){if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Setup: allow display over other apps first",1).show();return;}try{startForegroundService(new Intent(this,IslandService.class));}catch(Exception e){Toast.makeText(this,e.toString(),1).show();}}
  
   }
