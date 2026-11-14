// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.*;
import android.net.wifi.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private EditText ssid;
    private TextView connection,vehicle,logView,timerView;
    private Spinner duration,delay;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<View> controls=new ArrayList<>();
    private final StringBuilder log=new StringBuilder();
    private VehicleClient client;
    private TutorialGuide tutorial;
    private volatile boolean connected=false;
    private volatile int generation=0;
    private final Map<Integer,byte[]> registers=new ConcurrentHashMap<>();
    private final Map<Integer,Long> receivedAt=new ConcurrentHashMap<>();
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network selectedNetwork;
    private Button connectButton;
    private Button manualConnectButton;
    private Button accessCheckButton;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private Runnable networkDeadline;
    private NetworkGate networkGate;
    private volatile Socket connectingSocket;
    private volatile VehicleClient connectingClient;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);boolean usedBefore=getPreferences(0).contains("diagnostics");cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        buildUi();
        log.append(getPreferences(0).getString("diagnostics",""));setControls(false);renderStatus();
        tutorial=new TutorialGuide(this,new TutorialGuide.Host(){
            public boolean connected(){return connected;}
            public int session(){return generation;}
            public boolean busy(){return selectedNetwork!=null&&!connected||connected&&!controlsEnabled;}
            public String message(){return actionMessage.getVisibility()==View.VISIBLE?actionMessage.getText().toString():connection.getText().toString();}
            public void connect(){connectManually();}
            public void disconnect(){MainActivity.this.disconnect("Disconnected");}
            public void register(){registration();}
            public void diagnostics(){copyDiagnostics();}
            public void controls(){selectPage(0);}
        });
        boolean showGuide=saved!=null&&saved.getBoolean("tutorialOpen",false)||TutorialFlow.firstRun(tutorial.seen(),usedBefore);
        if(usedBefore)tutorial.markSeen();
        if(showGuide)uiHandler.post(()->{if(!isFinishing()&&!isDestroyed())tutorial.show();});
        note("--- App session ---");note("Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+" / Open PHEV 0.4.1");
    }
    private final LiveStatus liveStatus=new LiveStatus();
    private final PhevUi.Feature[] climateTiles=new PhevUi.Feature[4];
    private PhevUi.Feature headlightTile,parkingTile;
    private TextView batteryValue,chargeState,powerState,linkState,actionMessage,clockView,stopCaption,timerMode,chargeTimerView,chargeTimerSetting;
    private Button disconnectButton;
    private TextView climateSummary,nextClimate,nextCharge,timerFeedback;
    private boolean controlsEnabled;
    private boolean updatingTimerSwitches;
    private int selectedPage;
    private final TextView[][] slotLabels=new TextView[2][5];
    private final Switch[][] slotSwitches=new Switch[2][5];
    private String timerSaveMessage="";
    private final ScrollView[] pages=new ScrollView[3];
    private final Button[] tabs=new Button[3];
    private final Runnable statusTick=new Runnable(){public void run(){renderStatus();uiHandler.postDelayed(this,1000);}};
    private void buildUi(){
        PhevUi.configure(this);
        LinearLayout shell=PhevUi.column(this);shell.setBackgroundColor(PhevUi.BG);setContentView(shell);
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        WindowInsetsController insetsController=getWindow().getInsetsController();if(insetsController!=null)insetsController.setSystemBarsAppearance(PhevUi.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout header=PhevUi.row(this);header.setPadding(dp(22),dp(20),dp(22),dp(14));shell.addView(header);
        LinearLayout brand=PhevUi.column(this);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView wordmark=label(brand,"Open PHEV",27,PhevUi.INK);wordmark.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        TextView subtitle=label(brand,"OUTLANDER · LOCAL CONTROL",10,PhevUi.MUTED);subtitle.setLetterSpacing(.12f);subtitle.setPadding(0,dp(3),0,0);
        linkState=PhevUi.label(this,"Offline",12,PhevUi.MUTED);linkState.setPadding(dp(12),dp(8),dp(12),dp(8));header.addView(linkState);
        FrameLayout stage=new FrameLayout(this);shell.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout[] content=new LinearLayout[3];
        for(int i=0;i<3;i++){pages[i]=new ScrollView(this);pages[i].setFillViewport(true);pages[i].setClipToPadding(false);content[i]=PhevUi.column(this);content[i].setPadding(dp(20),dp(6),dp(20),dp(24));pages[i].addView(content[i]);stage.addView(pages[i],new FrameLayout.LayoutParams(-1,-1));}
        root=content[0];
        LinearLayout overview=card(root);
        label(overview,"YOUR VEHICLE",11,PhevUi.MUTED).setLetterSpacing(.1f);
        LinearLayout batteryRow=PhevUi.row(this);overview.addView(batteryRow);
        batteryValue=label(batteryRow,"—",42,PhevUi.INK);batteryValue.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
        LinearLayout details=PhevUi.column(this);LinearLayout.LayoutParams detailsParams=new LinearLayout.LayoutParams(0,-2,1);detailsParams.setMargins(dp(18),0,0,0);batteryRow.addView(details,detailsParams);
        chargeState=label(details,"Battery status unavailable",13,PhevUi.MUTED);powerState=label(details,"Connect to see your car",13,PhevUi.MUTED);
        climateSummary=label(overview,"Climate · connect to see status",16,PhevUi.MUTED);
        vehicle=label(overview,"Live status appears after connection.",13,PhevUi.MUTED);
        connection=label(overview,"Join the car's Wi-Fi in Android settings, then connect.",13,PhevUi.MUTED);connection.setPadding(0,dp(8),0,dp(8));
        manualConnectButton=addButton(overview,"Connect to vehicle",true,this::connectManually,false);
        LinearLayout sessionActions=PhevUi.row(this);overview.addView(sessionActions);
        Button refresh=addButton(sessionActions,"Refresh status",false,()->command("Request status",6,new byte[]{3}),true);weight(refresh);
        disconnectButton=addButton(sessionActions,"Disconnect",false,()->disconnect("Disconnected"),false);weight(disconnectButton);
        actionMessage=label(root,"",13,PhevUi.MUTED);actionMessage.setPadding(dp(4),dp(8),dp(4),dp(4));actionMessage.setVisibility(View.GONE);actionMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout upcoming=card(root);label(upcoming,"UP NEXT",11,PhevUi.MUTED).setLetterSpacing(.1f);
        nextClimate=label(upcoming,"Climate · connect to read schedules",16,PhevUi.INK);
        nextCharge=label(upcoming,"Charging · connect to read schedules",16,PhevUi.INK);
        label(upcoming,"Planned starts from the car's clock, not confirmation of a run.",12,PhevUi.MUTED);
        addButton(upcoming,"View schedules →",false,()->selectPage(1),false);
        heading(root,"Cabin comfort","A comfortable cabin, before you set off.");
        LinearLayout options=PhevUi.row(this);root.addView(options);
        LinearLayout run=PhevUi.column(this),when=PhevUi.column(this);options.addView(run,new LinearLayout.LayoutParams(0,-2,1));options.addView(when,new LinearLayout.LayoutParams(0,-2,1));
        label(run,"Run for",12,PhevUi.MUTED);duration=choice(run,new String[]{"10 minutes","20 minutes","30 minutes"});duration.setContentDescription("Climate run time");
        label(when,"Start",12,PhevUi.MUTED);delay=choice(when,new String[]{"Now","In 5 minutes","In 10 minutes"});delay.setContentDescription("Climate start delay");
        climateTiles[2]=feature(root,"Heat cabin","heat",()->climate(2,"Heating"));
        climateTiles[3]=feature(root,"Demist / defrost","screen",()->climate(3,"Windscreen mode"));
        climateTiles[1]=feature(root,"Cool cabin","cool",()->climate(1,"Cooling"));
        addButton(root,"Stop climate",false,()->command("Stop climate",0x1b,new byte[]{1,0,0,0}),true);
        stopCaption=label(root,"Not connected",12,PhevUi.MUTED);stopCaption.setGravity(Gravity.CENTER);
        TextView legend=label(root,"Green means the vehicle confirms it is active. Amber means a request is awaiting confirmation. Refresh if status becomes unknown.",12,PhevUi.MUTED);legend.setPadding(dp(4),dp(12),dp(4),0);
        heading(root,"Lights","Find your car or light the way.");
        headlightTile=lightCard(root,"Headlights",10);parkingTile=lightCard(root,"Parking lights",11);
        root=content[1];heading(root,"Timers","Stored in your car. Ready when you are.");
        timerFeedback=label(root,"",14,PhevUi.GREEN);timerFeedback.setVisibility(View.GONE);timerFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout clockCard=card(root);label(clockCard,"VEHICLE CLOCK",11,PhevUi.MUTED).setLetterSpacing(.1f);clockView=label(clockCard,"Connect to read the vehicle clock",18,PhevUi.INK);
        label(clockCard,"Schedules use the car's clock. Check it before saving a timer.",13,PhevUi.MUTED);
        LinearLayout timers=card(root);label(timers,"Climate schedule",21,PhevUi.INK);label(timers,"Stored in the vehicle · uses its clock",12,PhevUi.MUTED);
        timerMode=label(timers,"Connect to read the shared climate mode",17,PhevUi.GREEN);
        timerView=label(timers,"Connect to read your five timer slots.",14,PhevUi.MUTED);timerView.setPadding(0,dp(12),0,dp(12));
        timerCards(timers,false);
        addButton(timers,"Reconnect & reload timers",false,this::connectManually,true);
        
        label(timers,"One climate mode applies to all five slots. Manual climate commands can also change this mode. Times use 10-minute steps and the vehicle clock. Verify a scheduled run before relying on it.",13,PhevUi.MUTED);
        LinearLayout charging=card(root);label(charging,"Charging schedule",21,PhevUi.INK);label(charging,"Vehicle-reported saves · verify scheduled operation",12,PhevUi.AMBER);
        chargeTimerSetting=label(charging,"Connect to read charging settings",14,PhevUi.MUTED);
        chargeTimerView=label(charging,"Connect to read your charging timers.",14,PhevUi.MUTED);chargeTimerView.setPadding(0,dp(12),0,dp(12));
        timerCards(charging,true);
        
        label(charging,"Five slots, in 10-minute steps. Overnight end times are marked +1 day. Existing location restrictions are preserved. Test a scheduled charging session before relying on it.",13,PhevUi.MUTED);
        root=content[2];heading(root,"Settings","Connection, setup and support.");
        LinearLayout guide=card(root);label(guide,"Getting started",21,PhevUi.INK);label(guide,"Phone settings, pairing, beep counts and connection recovery, one step at a time.",14,PhevUi.MUTED);addButton(guide,"Tutorial",true,()->tutorial.show(),false);
        LinearLayout wifi=card(root);label(wifi,"Vehicle Wi-Fi",21,PhevUi.INK);label(wifi,"Join your vehicle's saved network in Android settings. Open PHEV does not store its password.",14,PhevUi.MUTED);
        addButton(wifi,"Open Wi-Fi settings",false,()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)),false);
        label(wifi,"Network name · optional",12,PhevUi.MUTED);
        ssid=new EditText(this);ssid.setSingleLine(true);ssid.setHint("e.g. REMOTEabcdef");ssid.setTextColor(PhevUi.INK);ssid.setHintTextColor(PhevUi.MUTED);ssid.setTextSize(16);ssid.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);ssid.setText(getPreferences(0).getString("ssid",""));wifi.addView(ssid);
        connectButton=addButton(wifi,"Connect using network name",false,this::connect,false);
        label(wifi,"The main Connect button uses the Wi-Fi you have already joined, even when Android hides its name.",13,PhevUi.MUTED);
        LinearLayout pairing=card(root);label(pairing,"Phone registration",21,PhevUi.INK);label(pairing,"Only needed for initial setup. Keep the vehicle network's MAC setting unchanged after registering.",14,PhevUi.MUTED);
        addButton(pairing,"Register this phone…",false,this::registration,true);addButton(pairing,"Registration help",false,this::registrationHelp,false);
        LinearLayout support=card(root);label(support,"Diagnostics",21,PhevUi.INK);label(support,"Copy the log if you need help. VIN, passwords and MAC addresses are omitted.",14,PhevUi.MUTED);
        addButton(support,"Copy diagnostics",true,this::copyDiagnostics,false);accessCheckButton=addButton(support,"Check phone Wi-Fi access",false,this::checkWifiAccess,false);
        logView=PhevUi.label(this,"",11,PhevUi.MUTED);logView.setTextIsSelectable(true);logView.setTypeface(android.graphics.Typeface.MONOSPACE);logView.setVisibility(View.GONE);
        addButton(support,"Show / hide diagnostic log",false,()->logView.setVisibility(logView.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE),false);support.addView(logView);
        label(root,"Open PHEV 0.4.1 · GPL-3.0-or-later",13,PhevUi.INK);label(root,"Independent open-source project. Not affiliated with Mitsubishi. Control works within the vehicle's Wi-Fi range. Lock status is read-only; remote locking is not implemented.",12,PhevUi.MUTED);
        LinearLayout navigation=PhevUi.row(this);navigation.setPadding(dp(16),dp(10),dp(16),dp(12));navigation.setBackgroundColor(PhevUi.SURFACE);shell.addView(navigation);
        String[] names={"Controls","Timers","Settings"};for(int i=0;i<3;i++){final int index=i;tabs[i]=PhevUi.button(this,names[i],false,()->selectPage(index));navigation.addView(tabs[i],new LinearLayout.LayoutParams(0,-2,1));}
        selectPage(0);
    }
    private int dp(int n){return PhevUi.dp(this,n);}
    private TextView label(LinearLayout parent,String s,int size,int color){TextView t=PhevUi.label(this,s,size,color);t.setPadding(0,dp(3),0,dp(5));parent.addView(t);return t;}
    private LinearLayout card(LinearLayout parent){LinearLayout c=PhevUi.column(this);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(PhevUi.shape(this,PhevUi.SURFACE,PhevUi.LINE,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));parent.addView(c,p);return c;}
    private void heading(LinearLayout parent,String title,String subtitle){TextView t=label(parent,title,23,PhevUi.INK);t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));t.setPadding(0,dp(14),0,dp(2));label(parent,subtitle,13,PhevUi.MUTED).setPadding(0,0,0,dp(15));}
    private Button addButton(LinearLayout parent,String title,boolean primary,Runnable action,boolean control){Button b=PhevUi.button(this,title,primary,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));parent.addView(b,p);if(control)controls.add(b);return b;}
    private void weight(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(6),dp(3),dp(4));v.setLayoutParams(p);}
    private Spinner choice(LinearLayout parent,String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setMinimumHeight(dp(48));parent.addView(s,new LinearLayout.LayoutParams(-1,-2));return s;}
    private PhevUi.Feature feature(LinearLayout parent,String title,String icon,Runnable action){PhevUi.Feature f=new PhevUi.Feature(this,title,icon,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));parent.addView(f,p);controls.add(f);return f;}
    private PhevUi.Feature lightCard(LinearLayout parent,String title,int reg){LinearLayout c=card(parent);PhevUi.Feature f=new PhevUi.Feature(this,title,"lights",null);c.addView(f,new LinearLayout.LayoutParams(-1,-2));LinearLayout row=PhevUi.row(this);c.addView(row);Button on=addButton(row,"Turn on",false,()->command(title+" on",reg,new byte[]{1}),true);weight(on);Button off=addButton(row,"Turn off",false,()->command(title+" off",reg,new byte[]{2}),true);weight(off);return f;}
    private void selectPage(int index){selectedPage=index;for(int i=0;i<3;i++){pages[i].setVisibility(i==index?View.VISIBLE:View.GONE);tabs[i].setSelected(i==index);tabs[i].setTextColor(i==index?PhevUi.GREEN:PhevUi.MUTED);tabs[i].setBackground(PhevUi.ripple(this,i==index?PhevUi.PALE:PhevUi.SURFACE,0,14));}renderStatus();}
    private void timerSaved(String message){
        timerSaveMessage=message+" · "+new java.text.SimpleDateFormat("HH:mm",Locale.UK).format(new Date())+" (phone time)";
    }
    private void timerCards(LinearLayout parent,boolean charge){
        int kind=charge?1:0;
        for(int i=0;i<5;i++){
            final int slot=i;LinearLayout row=PhevUi.row(this);row.setPadding(dp(10),dp(8),dp(10),dp(8));
            row.setBackground(PhevUi.shape(this,PhevUi.BG,0,14));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,dp(5),0,dp(5));parent.addView(row,rp);
            TextView title=PhevUi.label(this,"Timer "+(i+1),15,PhevUi.INK);title.setPadding(dp(6),dp(12),dp(8),dp(12));title.setMinimumHeight(dp(64));
            title.setBackground(PhevUi.ripple(this,PhevUi.BG,0,10));title.setFocusable(true);title.setOnClickListener(v->{if(charge)editChargeTimer(slot);else editTimer(slot);});
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));controls.add(title);slotLabels[kind][i]=title;
            Switch toggle=new Switch(this);toggle.setText("");toggle.setMinimumHeight(dp(48));toggle.setMinimumWidth(dp(56));
            toggle.setOnCheckedChangeListener((button,checked)->{if(updatingTimerSwitches)return;updatingTimerSwitches=true;button.setChecked(!checked);updatingTimerSwitches=false;toggleTimer(charge,slot);});row.addView(toggle);slotSwitches[kind][i]=toggle;
        }
        label(parent,"Tap a timer to edit. Switch changes are reviewed before saving.",12,PhevUi.MUTED);
    }
    private void renderTimerCards(){
        updatingTimerSwitches=true;
        for(int k=0;k<2;k++)for(int i=0;i<5;i++){
            if(slotLabels[k][i]==null)continue;boolean charge=k==1;byte[] read=registers.get(charge?4:5);boolean loaded=connected&&read!=null&&read.length==(charge?20:16);
            String title=loaded?(charge?ChargeTimers.describe(read,i):ClimateTimers.describe(read,i)):"Timer "+(i+1)+" · Waiting for vehicle";
            slotLabels[k][i].setText(title+"\nEdit ›");slotLabels[k][i].setContentDescription((charge?"Charging ":"Climate ")+title+". Tap to edit");
            slotSwitches[k][i].setChecked(loaded&&SchedulePreview.enabled(read,i,charge));
            slotSwitches[k][i].setEnabled(controlsEnabled&&loaded&&SchedulePreview.editable(read,i,charge));
            slotSwitches[k][i].setContentDescription((charge?"Charging":"Climate")+" timer "+(i+1)+", "+(slotSwitches[k][i].isChecked()?"enabled":"disabled")+". Review change");
        }
        updatingTimerSwitches=false;
        if(timerFeedback!=null){timerFeedback.setText(timerSaveMessage);timerFeedback.setVisibility(timerSaveMessage.isEmpty()?View.GONE:View.VISIBLE);}
    }
    private void toggleTimer(boolean charge,int slot){
        if(!connected||!controlsEnabled)return;
        try{
            byte[] value=registers.get(charge?4:5);if(value==null)return;final byte[] snapshot=value.clone();
            int setting=charge?ChargeTimers.setting(registers.get(0x1b)):ClimateTimers.mode(registers.get(0x1c));
            byte[] write=SchedulePreview.toggle(snapshot,slot,charge,setting);int token=generation;
            String review=charge?ChargeTimers.describe(Arrays.copyOf(write,20),slot)+"\n"+ChargeTimers.settingName(write[20]):ClimateTimers.describe(ClimateTimers.expectedRead(write,snapshot[0]),slot)+"\n"+ClimateTimers.modeName(setting);
            LinearLayout form=PhevUi.column(this);form.setPadding(dp(22),dp(10),dp(22),dp(10));label(form,review+"\n\nOther slots retain their settings. Uses the vehicle clock.",16,PhevUi.INK);
            TextView outcome=label(form,"",13,PhevUi.AMBER);outcome.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Change timer "+(slot+1)+"?").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save to vehicle",null).create();
            dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(charge)saveChargeTimer(dialog,outcome,snapshot,setting,write,token);else saveTimer(dialog,outcome,snapshot,setting,write,token);}));dialog.show();
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private String upcoming(boolean charge,long now){
        String title=charge?"Charging":"Climate";if(!connected)return title+" · Connect to read schedules";
        byte[] read=registers.get(charge?4:5),mode=registers.get(charge?0x1b:0x1c);
        int setting=charge?ChargeTimers.setting(mode):ClimateTimers.mode(mode);
        if(read==null||read.length!=(charge?20:16)||mode==null||mode.length!=1)return title+" · Waiting for schedule";
        if(charge?(setting<0||setting>3):(setting<1||setting>3))return title+" · Mode unknown";
        if(charge&&setting<2)return title+" · No active schedule setting";
        Long at=receivedAt.get(0x12);java.time.LocalDateTime clock=at==null?null:SchedulePreview.clock(registers.get(0x12),at,now);
        if(clock==null)return title+" · Refresh vehicle clock for next start";
        SchedulePreview.Next next=SchedulePreview.next(read,charge,setting,clock);
        if(next==null)return title+" · No enabled, supported timers";
        return (charge?"Charging":ClimateTimers.modeName(setting))+"\n"+next.describe(clock)+(charge&&setting==3?"\nAt a saved charging location":"");
    }
    private void registrationHelp(){tutorial.show();}
    private void feedback(String message){actionMessage.setText(message);actionMessage.setVisibility(View.VISIBLE);}
    @Override protected void onResume(){super.onResume();uiHandler.removeCallbacks(statusTick);uiHandler.post(statusTick);}
    @Override protected void onPause(){uiHandler.removeCallbacks(statusTick);super.onPause();}

    private void note(String s){runOnUiThread(()->{log.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())).append("  ").append(s).append('\n');if(log.length()>120000)log.delete(0,log.length()-96000);logView.setText(log.toString());getPreferences(0).edit().putString("diagnostics",log.toString()).apply();});}
    private void setControls(boolean enable){controlsEnabled=enable;for(View b:controls){b.setEnabled(enable);b.setAlpha(enable?1f:.5f);}renderTimerCards();if(tutorial!=null)tutorial.refresh();}
    private void copyDiagnostics(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Open PHEV diagnostics",log.toString()));Toast.makeText(this,"Diagnostics copied",Toast.LENGTH_SHORT).show();}
    private void progress(String message){connection.setText(message);note(message);}
    private void problem(String message){progress(message);new AlertDialog.Builder(this).setTitle("Connection needs attention").setMessage(message).setPositiveButton("OK",null).setNeutralButton("Copy diagnostics",(d,w)->copyDiagnostics()).show();}
    private void connect(){
        try{connectChecked();}catch(Exception e){disconnect("Connection failed");problem(e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void connectChecked(){
        String name=ssid.getText().toString().trim();
        progress("1/3 · Checking Wi-Fi and permissions…");
        if(name.isEmpty()){ssid.setError("Enter your vehicle's Wi-Fi network name");problem("Enter the vehicle SSID, or use 'Use connected Wi-Fi' after joining the car's network in Samsung settings.");return;}
        ArrayList<String> permissions=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)permissions.add(p);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if(!permissions.isEmpty()){progress("Waiting for permission to read the Wi-Fi name. You can also use 'Use connected Wi-Fi' without granting location access.");requestPermissions(permissions.toArray(new String[0]),1);return;}
        Network found=null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c==null||!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            WifiInfo info=c.getTransportInfo() instanceof WifiInfo?(WifiInfo)c.getTransportInfo():null;
            if(info==null||WifiManager.UNKNOWN_SSID.equals(info.getSSID()))info=((WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();
            String actual=normalizeSsid(info==null?null:info.getSSID());
            note("Wi-Fi detected; SSID "+(actual.isEmpty()?"hidden by Android":name.equals(actual)?"matches":"does not match")+"; vehicle subnet="+onVehicleSubnet(n));
            if(name.equals(actual)){found=n;break;}
        }
        if(found==null){problem("The entered SSID could not be verified. If Samsung settings show the phone is already connected to the car, tap 'Use connected Wi-Fi'. Android may be hiding the network name even though Wi-Fi works.");return;}
        beginConnection(found,name);
    }
    private static String normalizeSsid(String value){if(value==null||WifiManager.UNKNOWN_SSID.equals(value))return "";if(value.startsWith("\"")&&value.endsWith("\""))return value.substring(1,value.length()-1);return value;}
    private boolean onVehicleSubnet(Network network){
        return onVehicleSubnet(cm.getLinkProperties(network));
    }
    private boolean onVehicleSubnet(LinkProperties properties){
        if(properties==null)return false;
        for(LinkAddress link:properties.getLinkAddresses())if(link.getAddress() instanceof Inet4Address){byte[] ip=link.getAddress().getAddress();if((ip[0]&255)==192&&(ip[1]&255)==168&&(ip[2]&255)==8)return true;}
        return false;
    }
    private void checkWifiAccess(){
        try{
            if(connected){problem("Disconnect the vehicle session before running the phone access check.");return;}
            ArrayList<Network> matches=new ArrayList<>();
            for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&onVehicleSubnet(n))matches.add(n);}
            if(matches.size()!=1){problem("Join the car's Wi-Fi in Samsung settings first. The phone access check needs one connected Wi-Fi network on 192.168.8.x, but does not need pairing mode or a MAC entry.");return;}
            prepareNetwork(matches.get(0),true);
        }catch(Exception e){connectionFailure("Phone access check: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void connectionFailure(String reason){
        disconnect(reason);
        new AlertDialog.Builder(this).setTitle("Connection needs attention").setMessage(reason).setPositiveButton("OK",null).setNeutralButton("Copy diagnostics",(d,w)->copyDiagnostics()).show();
    }
    private void connectManually(){
        try{
            progress("1/3 · Looking for the connected vehicle Wi-Fi…");
            ArrayList<Network> candidates=new ArrayList<>();int wifiCount=0;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities caps=cm.getNetworkCapabilities(n);
                if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
                wifiCount++;if(onVehicleSubnet(n))candidates.add(n);
            }
            note("Wi-Fi networks="+wifiCount+"; vehicle-subnet candidates="+candidates.size());
            if(candidates.size()!=1){problem(candidates.isEmpty()?"No connected Wi-Fi network on the vehicle's 192.168.8.x subnet was found. Open Samsung Wi-Fi settings, reconnect to the car, and choose to stay connected without internet. Then try again.":"More than one Wi-Fi network matches the vehicle subnet. Disconnect the other Wi-Fi connection and try again.");return;}
            Network network=candidates.get(0);String name=ssid.getText().toString().trim();int token=generation;
            new AlertDialog.Builder(this).setTitle("Use your current Wi-Fi?").setMessage("Confirm Samsung Wi-Fi settings show you connected to your vehicle. This option uses that Wi-Fi connection without requiring Android to reveal its name. It will try the car's local address, 192.168.8.46. It does not send registration or climate commands.").setNegativeButton("Cancel",(d,w)->progress("Connection cancelled")).setPositiveButton("I'm on the car Wi-Fi",(d,w)->{if(token!=generation)return;try{if(!onVehicleSubnet(network))throw new IllegalStateException("Wi-Fi changed; reconnect to the car and try again");beginConnection(network,name);}catch(Exception e){disconnect("Connection failed");problem(e.getClass().getSimpleName()+": "+e.getMessage());}}).show();
        }catch(Exception e){problem(e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);if(request!=1)return;
        boolean granted=results.length>0;for(int result:results)if(result!=PackageManager.PERMISSION_GRANTED)granted=false;
        if(granted)connect();else problem("Permission to read the Wi-Fi name was not fully granted. You can use 'Use connected Wi-Fi' instead, after joining the car in Samsung Wi-Fi settings.");
    }
    private void beginConnection(Network network,String name){
        getPreferences(0).edit().putString("ssid",name).apply();
        prepareNetwork(network,false);
    }
    private void prepareNetwork(Network expected,boolean checkOnly){
        disconnect("2/3 · Requesting access to the selected Wi-Fi (up to 12 seconds)…");
        int token=generation;selectedNetwork=expected;
        connectButton.setEnabled(false);manualConnectButton.setEnabled(false);accessCheckButton.setEnabled(false);ssid.setEnabled(false);
        Network active=cm.getActiveNetwork();NetworkCapabilities activeCaps=active==null?null:cm.getNetworkCapabilities(active);
        note("Network check: default VPN="+(activeCaps!=null&&activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))+", INTERNET permission="+(checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED)+", Nearby Wi-Fi permission="+(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED));
        NetworkGate gate=new NetworkGate(expected.getNetworkHandle());networkGate=gate;
        networkCallback=new ConnectivityManager.NetworkCallback(){
            private boolean current(Network n){return token==generation&&gate.matches(n.getNetworkHandle());}
            private void tryOpen(Network n){if(current(n)&&gate.claim()){
                if(networkDeadline!=null){uiHandler.removeCallbacks(networkDeadline);networkDeadline=null;}
                progress(checkOnly?"Android supplied the selected Wi-Fi; checking socket access…":"2/3 · Wi-Fi ready; opening vehicle connection (up to 8 seconds)…");
                openSocket(n,checkOnly,token);
            }}
            @Override public void onAvailable(Network n){if(token!=generation)return;if(!gate.matches(n.getNetworkHandle())){connectionFailure("Android supplied a different Wi-Fi network. No vehicle connection was attempted. Rejoin the car Wi-Fi and retry.");return;}note("Network callback: selected Wi-Fi available");gate.available(n.getNetworkHandle());tryOpen(n);}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities caps){if(!current(n))return;boolean wifi=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),vpn=caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);note("Network callback: Wi-Fi="+wifi+", VPN="+vpn+", unrestricted="+caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)+", validated="+caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));gate.capabilities(n.getNetworkHandle(),wifi&&!vpn);if(!wifi||vpn){connectionFailure("Selected network changed transport; connection stopped.");return;}tryOpen(n);}
            @Override public void onLinkPropertiesChanged(Network n,LinkProperties props){if(!current(n))return;boolean subnet=onVehicleSubnet(props);note("Network callback: vehicle subnet="+subnet);gate.link(n.getNetworkHandle(),subnet);if(!subnet){connectionFailure("Selected Wi-Fi no longer has a vehicle-subnet address. Rejoin the car Wi-Fi.");return;}tryOpen(n);}
            @Override public void onBlockedStatusChanged(Network n,boolean blocked){if(!current(n))return;note("Network callback: access blocked="+blocked);gate.blocked(n.getNetworkHandle(),blocked);if(blocked){connectionFailure("Android reports this app is blocked from the selected Wi-Fi. No vehicle communication was attempted; copy diagnostics.");return;}tryOpen(n);}
            @Override public void onLost(Network n){if(current(n))connectionFailure("The selected Wi-Fi connection was lost.");}
            @Override public void onUnavailable(){if(token==generation)connectionFailure("Android did not make the selected Wi-Fi available to this app. Copy diagnostics.");}
        };
        networkDeadline=()->{if(token==generation)connectionFailure("Timed out waiting for Android's Wi-Fi availability, link and access status. No vehicle communication was attempted.");};
        uiHandler.postDelayed(networkDeadline,12000);
        try{cm.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),networkCallback,uiHandler,12000);}
        catch(Exception e){connectionFailure("Network request failed: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void openSocket(Network network,boolean checkOnly,int token){
        worker.execute(()->{
            Socket socket=null;VehicleClient candidate=null;
            try{
                if(token!=generation)return;
                socket=network.getSocketFactory().createSocket();
                if(token!=generation){socket.close();return;}connectingSocket=socket;
                note("Wi-Fi socket factory: bound socket created");
                if(checkOnly){socket.close();runOnUiThread(()->{if(token==generation)disconnect("PHONE ACCESS CHECK PASSED: Android allowed a Wi-Fi-bound socket. No TCP connection or vehicle commands were sent. Vehicle handshake remains a separate test.");});return;}
                socket.connect(new InetSocketAddress("192.168.8.46",8080),8000);if(token!=generation){socket.close();return;}
                runOnUiThread(()->{if(token==generation)progress("3/3 · Wi-Fi socket connected; waiting for vehicle handshake (up to 20 seconds)…");});
                candidate=new VehicleClient(socket,new VehicleClient.Listener(){
                    public void state(String s){if(token==generation)note(s);}
                    public void register(int reg,byte[] data){long received=System.nanoTime();runOnUiThread(()->{if(token!=generation)return;byte[] previous=registers.put(reg,data);receivedAt.put(reg,received);if(previous==null)note(String.format(Locale.ROOT,"Received register %02x (%d bytes)",reg,data.length));if(!Arrays.equals(previous,data)&&(reg==0x10||reg==0x1a||reg==0x1c||reg==0x02||reg==0x1d||reg==0x24))note(String.format(Locale.ROOT,"Status register %02x = %s",reg,Protocol.hex(data)));renderStatus();});}
                    public void lost(String s){runOnUiThread(()->{if(token==generation)disconnect(s);});}
                });
                connectingClient=candidate;VehicleClient readyClient=candidate;candidate.start();candidate.awaitReady();
                runOnUiThread(()->{if(token!=generation){readyClient.close();return;}client=readyClient;connected=true;connection.setText("Live updates from your vehicle");setControls(true);note("Connection ready. Registration is separate and only needed for a new phone identity.");command("Initial status request",6,new byte[]{3});});
            }catch(Exception e){if(candidate!=null)candidate.close();else if(socket!=null)try{socket.close();}catch(Exception ignored){}runOnUiThread(()->{if(token==generation){String reason=(checkOnly?"Phone access check failed: ":"Connection failed: ")+e.getClass().getSimpleName()+": "+e.getMessage();if(String.valueOf(e.getMessage()).contains("EPERM"))reason+=". Android denied access even after the network request; copy diagnostics. Pairing the car cannot change this phone-side result.";connectionFailure(reason);}});}
        });
    }
    private void disconnect(String reason){generation++;if(networkGate!=null){networkGate.cancel();networkGate=null;}if(networkDeadline!=null){uiHandler.removeCallbacks(networkDeadline);networkDeadline=null;}connected=false;if(connectingClient!=null){connectingClient.close();connectingClient=null;}if(connectingSocket!=null){try{connectingSocket.close();}catch(Exception ignored){}connectingSocket=null;}if(client!=null){client.close();client=null;}if(networkCallback!=null){try{cm.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}networkCallback=null;}selectedNetwork=null;registers.clear();receivedAt.clear();liveStatus.clear();timerSaveMessage="";actionMessage.setVisibility(View.GONE);setControls(false);connectButton.setEnabled(true);manualConnectButton.setEnabled(true);accessCheckButton.setEnabled(true);ssid.setEnabled(true);connection.setText(reason);vehicle.setText("No live vehicle data · disconnected");timerView.setText("Connect to read vehicle climate timers.");note(reason);renderStatus();}
    private interface Action{void run(VehicleClient c)throws Exception;}
    private void task(String label,Action action){task(label,action,-1);}
    private void task(String label,Action action,int feature){
        if(!connected||client==null)return;
        VehicleClient c=client;int token=generation;setControls(false);feedback(label+" · sending…");note(label+" requested");renderStatus();
        worker.execute(()->{
            try{action.run(c);runOnUiThread(()->{if(token!=generation)return;if(feature>=0)liveStatus.acknowledged(feature);String message=label.equals("Phone registration")?"Registration received. Reconnect with the car OFF to verify.":label.equals("Climate timer")?"Climate schedule · saved and read back":label+" · request received";feedback(message);note(label.equals("Phone registration")?"Registration command acknowledged. Saved registration is NOT yet verified; normal reconnection with the car OFF is the verification step.":label+": acknowledged by vehicle; check live status.");renderStatus();});}
            catch(Exception e){runOnUiThread(()->{if(token!=generation)return;if(feature>=0)liveStatus.failed(feature);feedback(label+" · "+e.getMessage());note(label+": "+e.getMessage());renderStatus();});}
            finally{runOnUiThread(()->{if(token==generation)setControls(connected);});}
        });
    }
    private void command(String label,int reg,byte[] data){
        if(!connected||client==null)return;
        int feature=-1,target=0,wait=0;
        if(reg==0x1b&&data.length==4){feature=LiveStatus.CLIMATE;target=data[0]==1?0:data[1]&255;wait=(data[3]&255)*5;}
        else if(reg==10||reg==11){feature=reg==10?LiveStatus.HEADLIGHTS:LiveStatus.PARKING;target=data[0]==1?1:0;}
        if(feature>=0)liveStatus.begin(feature,target,wait,System.nanoTime());
        task(label,c->c.write(reg,data),feature);
    }

    private void registration(){new AlertDialog.Builder(this).setTitle("Register this phone?").setMessage("Put the car in registration mode first (see Registration help). This adds the phone using its current Wi-Fi identity. It does not reset existing registrations.").setNegativeButton("Cancel",null).setPositiveButton("Register",(d,w)->task("Phone registration",VehicleClient::registerPhone)).show();}
    private void climate(int mode,String label){int mins=(duration.getSelectedItemPosition()+1)*10,wait=delay.getSelectedItemPosition()*5;new AlertDialog.Builder(this).setTitle(label).setMessage("Request "+mins+" minutes"+(wait==0?" now":" in "+wait+" minutes")+"? The vehicle must be fully OFF (not ACC), with doors, bonnet and tailgate closed. An acknowledgement alone does not confirm climate started.").setNegativeButton("Cancel",null).setPositiveButton("Start",(d,w)->{for(int reg:new int[]{0x02,0x10,0x1a,0x1c,0x1d,0x24}){byte[] value=registers.get(reg);Long timestamp=receivedAt.get(reg);note(String.format(Locale.ROOT,"Before climate: register %02x = %s; age=%s",reg,value==null?"not received":Protocol.hex(value),timestamp==null?"unknown":((System.nanoTime()-timestamp)/1_000_000_000L)+"s"));}command(label,0x1b,Protocol.climate(mode,mins,wait));}).show();}
    private boolean current(int reg,int length,long now){byte[] b=registers.get(reg);return connected&&b!=null&&b.length==length&&LiveStatus.fresh(receivedAt,reg,now);}
    private void renderStatus(){
        if(batteryValue==null||ssid==null)return;
        long now=System.nanoTime();
        linkState.setText(connected?"● Connected":selectedNetwork!=null?"Connecting…":"Offline");linkState.setTextColor(connected?PhevUi.GREEN:PhevUi.MUTED);linkState.setBackground(PhevUi.shape(this,connected?PhevUi.PALE:PhevUi.SURFACE,0,20));
        manualConnectButton.setVisibility(connected?View.GONE:View.VISIBLE);disconnectButton.setVisibility(connected||selectedNetwork!=null?View.VISIBLE:View.GONE);
        ((View)disconnectButton.getParent()).setVisibility(connected||selectedNetwork!=null?View.VISIBLE:View.GONE);
        byte[] b=registers.get(0x1d);batteryValue.setText(current(0x1d,4,now)&&(b[0]&255)<=100?(b[0]&255)+"%":"—");
        b=registers.get(0x1f);chargeState.setText(current(0x1f,3,now)?b[0]==1?"● Charging":b[0]==0?"Not charging":"Charge status unknown":connected?"Refresh for charge status":"Battery status unavailable");chargeState.setTextColor(current(0x1f,3,now)&&b[0]==1?PhevUi.GREEN:PhevUi.MUTED);
        b=registers.get(0x1a);powerState.setText(current(0x1a,5,now)?b[0]==0?"Vehicle off":b[0]==3?"Accessory power on":b[0]==4?"Vehicle on":"Power state unknown":connected?"Waiting for vehicle status":"Connect to see your car");
        b=registers.get(0x24);String summary=connected?"Status becomes unknown after two minutes without an update.":"Live status appears after connection.";
        if(current(0x24,10,now)){boolean open=false,unknown=false;for(int i=3;i<9;i++){open|=b[i]==1;unknown|=b[i]!=0&&b[i]!=1;}summary=(open?"A door, bonnet or boot is open":unknown?"Door status unknown":"Doors, bonnet and boot closed")+" · "+(b[0]==1?"Locked":b[0]==2?"Unlocked":"Lock status unknown");}
        vehicle.setText(summary);
        for(int mode=1;mode<=3;mode++)climateTiles[mode].show(liveStatus.indicator(LiveStatus.CLIMATE,mode,connected,registers,receivedAt,now));
        LiveStatus.Indicator stop=liveStatus.indicator(LiveStatus.CLIMATE,0,connected,registers,receivedAt,now);stopCaption.setText(stop.text);stopCaption.setTextColor(stop.tone==LiveStatus.Tone.WAITING?PhevUi.AMBER:PhevUi.MUTED);
        headlightTile.show(liveStatus.indicator(LiveStatus.HEADLIGHTS,1,connected,registers,receivedAt,now));parkingTile.show(liveStatus.indicator(LiveStatus.PARKING,1,connected,registers,receivedAt,now));
        b=registers.get(0x12);
        if(current(0x12,7,now)&&(b[1]&255)>=1&&(b[1]&255)<=12&&(b[2]&255)>=1&&(b[2]&255)<=31&&(b[3]&255)<24&&(b[4]&255)<60)clockView.setText(String.format(Locale.ROOT,"%02d:%02d · %02d/%02d/%04d",b[3]&255,b[4]&255,b[2]&255,b[1]&255,2000+(b[0]&255))+"\nLast received from vehicle");
        else clockView.setText(connected?"Refresh to read the vehicle clock":"Connect to read the vehicle clock");
        byte[] chargingRead=registers.get(4),chargingMode=registers.get(0x1b);
        timerView.setText(connected?"Vehicle-stored slots · changes need confirmation":"Connect to read climate timers");
        chargeTimerView.setText(connected?"Vehicle-stored slots · repeat days refer to start day":"Connect to read charging timers");
        renderTimerCards();nextClimate.setText(upcoming(false,now));nextCharge.setText(upcoming(true,now));
        String comfort=connected?"Climate · status unknown":"Climate · not connected";int comfortColor=PhevUi.MUTED;
        for(int mode=1;mode<=3;mode++){LiveStatus.Indicator state=liveStatus.indicator(LiveStatus.CLIMATE,mode,connected,registers,receivedAt,now);
            if(state.tone==LiveStatus.Tone.ACTIVE){comfort=ClimateTimers.modeName(mode)+" · Active";comfortColor=PhevUi.GREEN;}
            else if(state.tone==LiveStatus.Tone.WAITING){comfort=ClimateTimers.modeName(mode)+" · "+state.text;comfortColor=PhevUi.AMBER;break;}}
        if(stop.text.equals("Off · confirmed")&&comfortColor==PhevUi.MUTED)comfort="Climate off · confirmed";
        if(stop.tone==LiveStatus.Tone.WAITING){comfort="Climate · "+stop.text;comfortColor=PhevUi.AMBER;}
        climateSummary.setText(comfort);climateSummary.setTextColor(comfortColor);
        chargeTimerSetting.setText(connected&&chargingMode!=null&&chargingMode.length==1?ChargeTimers.settingName(ChargeTimers.setting(chargingMode)):"Charging setting unknown · reconnect to reload");
        timerMode.setText(connected&&registers.get(0x1c)!=null&&registers.get(0x1c).length==1?"Reported shared mode · "+ClimateTimers.modeName(ClimateTimers.mode(registers.get(0x1c))):connected?"Shared mode unknown · reconnect to reload":"Connect to read the shared climate mode");
        if(tutorial!=null)tutorial.refresh();
    }

    private NumberPicker timeWheel(LinearLayout parent,String name,String[] values){
        LinearLayout column=PhevUi.column(this);parent.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=label(column,name,12,PhevUi.MUTED);title.setGravity(Gravity.CENTER);
        NumberPicker picker=new NumberPicker(this);picker.setMinValue(0);picker.setMaxValue(values.length-1);picker.setDisplayedValues(values);picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);picker.setContentDescription(name);picker.setTextColor(PhevUi.INK);picker.setTextSize(dp(26));
        column.addView(picker,new LinearLayout.LayoutParams(-1,dp(156)));
        // Let the wheel own vertical drags inside the scrolling dialog.
        picker.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked()!=MotionEvent.ACTION_UP&&event.getActionMasked()!=MotionEvent.ACTION_CANCEL);return false;});
        return picker;
    }
    private void editTimer(){editTimer(0);}
    private void editTimer(int initialSlot){
        byte[] current=registers.get(5);
        if(current==null||current.length!=16||registers.get(0x1c)==null||registers.get(0x1c).length!=1){new AlertDialog.Builder(this).setTitle("Read the schedule first").setMessage("Reconnect and wait for the schedule and shared mode, then reopen this editor.").setPositiveButton("OK",null).show();return;}
        byte[] snapshot=current.clone();int token=generation,originalMode=ClimateTimers.mode(registers.get(0x1c));
        LinearLayout form=PhevUi.column(this);form.setPadding(dp(20),0,dp(20),0);
        label(form,"Choose when to start. Saving checks settings received during this connection before making a change.",13,PhevUi.MUTED);
        label(form,"Climate mode · applies to ALL five slots",14,PhevUi.INK);
        Spinner mode=choice(form,new String[]{"Choose climate mode…","Cool cabin","Heat cabin","Demist / defrost windscreen"});mode.setSelection(originalMode>=1&&originalMode<=3?originalMode:0);mode.setContentDescription("Shared climate timer mode");
        label(form,"Manual climate controls can also change this shared mode.",12,PhevUi.MUTED);
        Spinner slot=choice(form,new String[]{"Timer 1","Timer 2","Timer 3","Timer 4","Timer 5"});slot.setContentDescription("Timer slot");
        LinearLayout timeRow=PhevUi.row(this);form.addView(timeRow);
        String[] hours=new String[24];for(int i=0;i<24;i++)hours[i]=String.format(Locale.ROOT,"%02d",i);
        NumberPicker hour=timeWheel(timeRow,"Hour · 24-hour",hours),minute=timeWheel(timeRow,"Minute",new String[]{"00","10","20","30","40","50"});
        label(form,"Vehicle timers use 10-minute steps. For 06:45, choose 06:40 or 06:50.",12,PhevUi.MUTED);
        label(form,"Run for",13,PhevUi.MUTED);Spinner length=choice(form,new String[]{"10 minutes","20 minutes","30 minutes"});length.setContentDescription("Timer duration");
        CheckBox enabled=new CheckBox(this);enabled.setText("Enabled");form.addView(enabled);
        String[] names={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};CheckBox[] days=new CheckBox[7];for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(names[i]);form.addView(days[i]);}
        slot.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){int value=ClimateTimers.value(snapshot,pos);int h=(value>>12)&31,m=(value>>9)&7;hour.setValue(h<24?h:7);minute.setValue(m<6?m:0);length.setSelection(Math.min(2,value&3));enabled.setChecked(h<24&&m<6&&((value>>2)&127)!=0&&(value&(1<<17))!=0);for(int i=0;i<7;i++)days[i].setChecked((value&(1<<(i+2)))!=0);}});
        slot.setSelection(initialSlot);
        TextView outcome=label(form,"",13,PhevUi.AMBER);outcome.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Climate timer").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Review change",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            if(token!=generation||!connected)throw new IllegalArgumentException("Connection changed. Reconnect, then reopen the editor.");
            int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;
            byte[] write=ClimateTimers.update(snapshot,slot.getSelectedItemPosition(),hour.getValue(),minute.getValue()*10,(length.getSelectedItemPosition()+1)*10,mask,enabled.isChecked(),mode.getSelectedItemPosition());
            String preview=ClimateTimers.describe(ClimateTimers.expectedRead(write,snapshot[0]),slot.getSelectedItemPosition());
            new AlertDialog.Builder(this).setTitle("Save climate timer?").setMessage(ClimateTimers.modeName(write[15])+"\n"+preview+"\n\nThis mode applies to ALL five slots. Other slots keep their times and days. Uses the vehicle's clock.").setNegativeButton("Back",null).setPositiveButton("Save",(x,y)->saveTimer(dialog,outcome,snapshot,originalMode,write,token)).show();
        }catch(Exception e){outcome.setText(e.getMessage());Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();
    }
    private void saveTimer(AlertDialog dialog,TextView outcome,byte[] snapshot,int originalMode,byte[] write,int token){
        if(token!=generation||!connected||client==null){outcome.setText("Reconnect, then reopen the editor. Nothing was sent.");return;}
        VehicleClient c=client;setControls(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);outcome.setText("Checking the saved schedule…");
        worker.execute(()->{
            try{
                boolean changed=c.saveClimateTimer(snapshot,originalMode,write);
                runOnUiThread(()->{if(token!=generation)return;dialog.dismiss();String message=(changed?"Saved to vehicle · ":"Already saved in vehicle · ")+ClimateTimers.modeName(write[15]);timerSaved(message);feedback(message);note(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();renderStatus();});
            }catch(Exception e){final String message=e.getMessage();runOnUiThread(()->{outcome.setText(message);note("Climate timer: "+message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();});}
            finally{runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);if(token==generation)setControls(connected);});}
        });
    }
    private void editChargeTimer(){editChargeTimer(0);}
    private void editChargeTimer(int initialSlot){
        byte[] read=registers.get(4),mode=registers.get(0x1b);int setting=ChargeTimers.setting(mode);
        if(read==null||read.length!=20||setting<0||setting>3){new AlertDialog.Builder(this).setTitle("Charging settings unavailable").setMessage("Reconnect & reload timers, then wait for the charging schedule. This editor needs a supported schedule and shared setting.").setPositiveButton("OK",null).show();return;}
        final byte[] snapshot=read.clone();final int token=generation;
        LinearLayout form=PhevUi.column(this);form.setPadding(dp(20),0,dp(20),0);
        label(form,"Experimental · test the first scheduled charge. Uses the vehicle clock; other slots retain their settings.",13,PhevUi.AMBER);
        label(form,setting==3?"Existing location restriction stays on. The car must be at a location it already recognises.":"Location restriction stays off. This app does not configure saved locations.",13,PhevUi.MUTED);
        Spinner slot=choice(form,new String[]{"Timer 1","Timer 2","Timer 3","Timer 4","Timer 5"});slot.setContentDescription("Charging timer slot");
        String[] hours=new String[24];for(int i=0;i<24;i++)hours[i]=String.format(Locale.ROOT,"%02d",i);String[] minutes={"00","10","20","30","40","50"};
        label(form,"Start charging · 24-hour clock",16,PhevUi.INK);LinearLayout start=PhevUi.row(this);form.addView(start);
        NumberPicker sh=timeWheel(start,"Start hour",hours),sm=timeWheel(start,"Start minute",minutes);
        label(form,"End charging · 24-hour clock",16,PhevUi.INK);LinearLayout end=PhevUi.row(this);form.addView(end);
        NumberPicker eh=timeWheel(end,"End hour",hours),em=timeWheel(end,"End minute",minutes);
        TextView interval=label(form,"",13,PhevUi.MUTED);
        Runnable showInterval=()->interval.setText(eh.getValue()*60+em.getValue()*10<sh.getValue()*60+sm.getValue()*10?"Ends the following day. Repeat days refer to the start day.":"Start and end are on the same day. Minutes use 10-minute steps.");
        for(NumberPicker wheel:new NumberPicker[]{sh,sm,eh,em})wheel.setOnValueChangedListener((v,a,b)->showInterval.run());
        CheckBox enabled=new CheckBox(this);enabled.setText("Enabled");form.addView(enabled);
        CheckBox full=new CheckBox(this);full.setText("Continue after end time until fully charged");form.addView(full);
        label(form,"Leave this off if charging must stop at the end of your chosen window.",12,PhevUi.MUTED);
        String[] names={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};CheckBox[] days=new CheckBox[7];for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(names[i]);form.addView(days[i]);}
        slot.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){
            boolean valid=ChargeTimers.validTimes(snapshot,pos);sh.setValue(valid?ChargeTimers.startHour(snapshot,pos):0);sm.setValue(valid?ChargeTimers.startMinute(snapshot,pos)/10:0);eh.setValue(valid?ChargeTimers.endHour(snapshot,pos):7);em.setValue(valid?ChargeTimers.endMinute(snapshot,pos)/10:0);
            enabled.setChecked(valid&&ChargeTimers.days(snapshot,pos)!=0&&ChargeTimers.enabled(snapshot,pos));full.setChecked(ChargeTimers.fullCharge(snapshot,pos));int mask=ChargeTimers.days(snapshot,pos);for(int i=0;i<7;i++)days[i].setChecked((mask&(1<<i))!=0);showInterval.run();
        }});
        slot.setSelection(initialSlot);
        TextView outcome=label(form,"",13,PhevUi.AMBER);outcome.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Charging timer").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Review change",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            if(token!=generation||!connected)throw new IllegalArgumentException("Reconnect, then reopen the editor. Nothing was sent.");
            int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;
            byte[] write=ChargeTimers.update(snapshot,slot.getSelectedItemPosition(),sh.getValue(),sm.getValue()*10,eh.getValue(),em.getValue()*10,mask,enabled.isChecked(),full.isChecked(),setting);
            String review=ChargeTimers.describe(Arrays.copyOf(write,20),slot.getSelectedItemPosition())+"\n\n"+ChargeTimers.settingName(write[20])+"\nOther slots are unchanged. Uses the vehicle clock. "+(full.isChecked()?"Charging may continue beyond the end time until full.":"Full-charge extension is off for this slot.");
            new AlertDialog.Builder(this).setTitle("Save charging timer?").setMessage(review).setNegativeButton("Back",null).setPositiveButton("Save",(a,b)->saveChargeTimer(dialog,outcome,snapshot,setting,write,token)).show();
        }catch(Exception e){outcome.setText(e.getMessage());Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();
    }
    private void saveChargeTimer(AlertDialog dialog,TextView outcome,byte[] snapshot,int setting,byte[] write,int token){
        if(token!=generation||!connected||client==null){outcome.setText("Reconnect, then reopen the editor. Nothing was sent.");return;}
        VehicleClient c=client;setControls(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);outcome.setText("Checking the stored charging schedule…");
        worker.execute(()->{
            try{boolean changed=c.saveChargeTimer(snapshot,setting,write);runOnUiThread(()->{if(token!=generation)return;dialog.dismiss();String message=changed?"Charging schedule saved to vehicle":"Charging schedule already saved in vehicle";timerSaved(message);feedback(message);note(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();renderStatus();});}
            catch(Exception e){runOnUiThread(()->{outcome.setText(e.getMessage());note("Charging timer: "+e.getMessage());Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}
            finally{runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);if(token==generation)setControls(connected);});}
        });
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("tutorialOpen",tutorial!=null&&tutorial.showing());}
    @Override protected void onDestroy(){if(tutorial!=null)tutorial.destroy();uiHandler.removeCallbacks(statusTick);disconnect("App closed");worker.shutdownNow();super.onDestroy();}
}






