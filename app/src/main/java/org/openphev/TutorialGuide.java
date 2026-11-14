// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Offline, first-use guide. Every connection/registration action is an explicit user tap. */
public final class TutorialGuide {
    public interface Host {
        boolean connected(); int session(); boolean busy(); String message();
        void connect(); void disconnect(); void register(); void diagnostics(); void controls();
    }
    private final Activity activity; private final Host host; private final SharedPreferences prefs;
    private final TutorialFlow flow;
    private Dialog dialog; private LinearLayout content; private ScrollView scroll;
    private TextView progress,title,status; private Button next,back,connect,register,disconnect;
    private CheckBox carOff; private int testSession;
    private static final String OFFICIAL="https://www.mitsubishi-motors.com/en/products/outlander_phev/app/remote/jizen.html";
    public TutorialGuide(Activity activity,Host host){this.activity=activity;this.host=host;prefs=activity.getSharedPreferences("tutorial",0);flow=new TutorialFlow(prefs.getInt("step",0),prefs.getBoolean("returnToWifi",false));}
    public boolean seen(){return prefs.getBoolean("seen",false);}
    public void markSeen(){prefs.edit().putBoolean("seen",true).apply();}
    public boolean showing(){return dialog!=null&&dialog.isShowing();}
    public void show(){
        if(showing())return;markSeen();
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=PhevUi.column(activity);shell.setBackgroundColor(PhevUi.BG);
        shell.setPadding(dp(18),dp(12),dp(18),dp(12));
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+bars.left,dp(12)+bars.top,dp(18)+bars.right,dp(12)+bars.bottom);return insets;});
        LinearLayout top=PhevUi.row(activity);shell.addView(top);
        TextView brand=PhevUi.label(activity,"SETUP GUIDE",12,PhevUi.GREEN);brand.setLetterSpacing(.12f);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button close=PhevUi.button(activity,"Close",false,this::close);top.addView(close);
        progress=PhevUi.label(activity,"",12,PhevUi.MUTED);progress.setPadding(0,dp(12),0,dp(6));shell.addView(progress);
        title=PhevUi.label(activity,"",25,PhevUi.INK);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));title.setPadding(0,0,0,dp(12));shell.addView(title);
        scroll=new ScrollView(activity);scroll.setFillViewport(true);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=PhevUi.column(activity);content.setPadding(0,0,0,dp(16));scroll.addView(content);
        LinearLayout nav=PhevUi.row(activity);shell.addView(nav);
        back=PhevUi.button(activity,"Back",false,()->{flow.back();render();});nav.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        next=PhevUi.button(activity,"Next",true,()->{if(flow.step()==TutorialFlow.DAILY){flow.restart();save();close();host.controls();}else{flow.next();render();}});
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.setMargins(dp(10),0,0,0);nav.addView(next,np);
        TextView footer=PhevUi.label(activity,"Reopen anytime: Settings → Tutorial",12,PhevUi.MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(10),0,0);shell.addView(footer);
        dialog.setContentView(shell);dialog.setOnCancelListener(d->save());dialog.show();
        Window window=dialog.getWindow();window.setLayout(-1,-1);window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowInsetsController controller=window.getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(PhevUi.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        render();
    }
    public void close(){save();if(dialog!=null)dialog.dismiss();}
    public void destroy(){if(dialog!=null)dialog.dismiss();dialog=null;}
    private void save(){prefs.edit().putInt("step",flow.step()).putBoolean("returnToWifi",flow.returningToWifi()).apply();}
    private int dp(int n){return PhevUi.dp(activity,n);}
    private void paragraph(String value){TextView v=PhevUi.label(activity,value,17,PhevUi.INK);v.setLineSpacing(dp(3),1);v.setPadding(0,dp(6),0,dp(16));content.addView(v);}
    private void hint(String value){TextView v=PhevUi.label(activity,value,14,PhevUi.MUTED);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(PhevUi.shape(activity,PhevUi.PALE,0,14));content.addView(v);}
    private Button action(String text,boolean primary,Runnable run){Button b=PhevUi.button(activity,text,primary,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(4));content.addView(b,p);return b;}
    private CheckBox check(String text){CheckBox c=new CheckBox(activity);c.setText(text);c.setTextColor(PhevUi.INK);c.setTextSize(16);c.setMinHeight(dp(52));content.addView(c);return c;}
    private void openSettings(String intent){try{activity.startActivity(new Intent(intent));}catch(ActivityNotFoundException e){Toast.makeText(activity,"Open Android Settings manually",Toast.LENGTH_LONG).show();}}
    private void official(){new AlertDialog.Builder(activity).setTitle("Open Mitsubishi's guide?").setMessage("This opens a website and needs internet. You may need to leave the car Wi-Fi temporarily. This tutorial works offline.").setNegativeButton("Stay here",null).setPositiveButton("Open guide",(d,w)->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(OFFICIAL)));}catch(ActivityNotFoundException e){Toast.makeText(activity,"No browser is available",Toast.LENGTH_LONG).show();}}).show();}
    private void jump(int step){flow.go(step);render();}
    private void connectionActions(boolean withRegistration){
        status=PhevUi.label(activity,"",14,PhevUi.MUTED);status.setPadding(dp(12),dp(12),dp(12),dp(12));status.setBackground(PhevUi.shape(activity,PhevUi.PALE,0,14));content.addView(status);
        connect=action("Connect to vehicle",true,host::connect);
        if(withRegistration)register=action("Register this phone…",false,host::register);
        disconnect=action("Disconnect",false,host::disconnect);
    }
    private void render(){
        save();content.removeAllViews();status=null;connect=null;register=null;disconnect=null;carOff=null;
        int step=flow.step();back.setEnabled(step>0);next.setEnabled(true);next.setText(step==TutorialFlow.DAILY?"Finish":step==TutorialFlow.RESET_WARNING?"Show reset steps":step==TutorialFlow.RESET_STEPS?"Reset done":step==TutorialFlow.RESET_DONE?"Set up again":"Next");
        progress.setText(step<=TutorialFlow.DAILY?"STEP "+(step+1)+" OF 9":"CONNECTION HELP · "+(step-8)+" OF 4");
        switch(step){
            case TutorialFlow.WELCOME:
                title.setText("Let's connect your car");
                paragraph("Set aside a few minutes beside your parked vehicle. Bring the key fob and the vehicle Wi-Fi card (network name and password). Put the car in P and apply the parking brake.");
                paragraph("This guide is for the MY18 Outlander PHEV Wi-Fi system. It takes you through phone settings, registration and a final check with the car OFF. Follow your own vehicle manual if its controls differ.");
                hint("Already using Open PHEV successfully? Close this guide. You do not need to register again after an app update.");
                action("Trouble connecting?",false,()->jump(TutorialFlow.TROUBLE));break;
            case TutorialFlow.VPN:
                title.setText("Turn off your VPN");
                paragraph("Disable any VPN before setup and while using Open PHEV on the car Wi-Fi. Check Samsung Secure Wi-Fi and any ad-blocking or firewall app that uses a VPN too.");
                paragraph("In Android Settings, search for VPN. If your VPN uses Always-on VPN or Block connections without VPN, turn those off for this connection too. A work-managed phone may need its administrator's help.");
                action("Open VPN settings",true,()->openSettings(Settings.ACTION_VPN_SETTINGS));
                hint("Open PHEV cannot turn a VPN off for you. Return here after changing the setting.");break;
            case TutorialFlow.WIFI:
                title.setText("Join the vehicle Wi-Fi");
                paragraph("Open Android Settings → Connections → Wi-Fi. Choose your vehicle's network using the name on its Wi-Fi card, usually starting REMOTE. Enter the card's password yourself.");
                paragraph("Choose Stay connected / Use this network if Android says there is no internet. That is expected: the phone talks directly to the car. Check that the car's network is still selected before returning here.");
                action("Open Wi-Fi settings",true,()->openSettings(Settings.ACTION_WIFI_SETTINGS));
                action("I cannot see the car Wi-Fi",false,()->{flow.wakeWifi();render();});
                hint("If the network is missing, enter pairing mode first. The guide will then bring you back here to join Wi-Fi.");break;
            case TutorialFlow.MAC:
                title.setText("Choose Phone MAC");
                paragraph("While connected to the car Wi-Fi, tap the cog beside that network. Open View more / Advanced if needed, then MAC address type → Phone MAC. Save if prompted, and reconnect to the same network.");
                paragraph("Other Android phones may call this Use device MAC. Change this for the vehicle network only. Leave it unchanged after registration, so the car recognises the same phone next time.");
                action("Open Wi-Fi settings",true,()->openSettings(Settings.ACTION_WIFI_SETTINGS));
                hint("You do not need to type, copy or share the MAC address in Open PHEV.");break;
            case TutorialFlow.PAIR:
                title.setText("Enter pairing mode");
                paragraph("Read this step before pressing anything. Sit inside with the key, driver's door shut, car in P and parking brake applied. Keep the hazard lights OFF.");
                paragraph("Start with power OFF. Keep your foot OFF the brake and press POWER once. ACC means accessory power: the button glows orange and READY stays off. It does not mean adaptive cruise control.");
                paragraph("Within 10 seconds of selecting ACC, alternate LOCK then UNLOCK on the key fob 10 presses total: five pairs. Listen for the entry beeps.");
                hint("If you miss the timing, return fully OFF and retry. Without the brake, the power sequence is OFF → ACC → ON → OFF. Do not hold the button or start READY.");break;
            case TutorialFlow.BEEPS:
                title.setText("Count the entry beeps");
                paragraph("1 beep → no phones saved\n2 beeps → one phone saved\n3 beeps → two phones saved (full)");
                paragraph("One sound followed by two quick sounds is THREE, not one. These counts do not identify your phone. Pairing lasts about five minutes.");
                hint("No beep? Repeat the pairing step. Three beeps? An old registration may need removing before adding a new phone.");
                action("Full / old registrations — help",false,()->jump(TutorialFlow.TROUBLE));break;
            case TutorialFlow.REGISTER:
                title.setText("Connect, then register");
                paragraph("Keep the car in pairing mode. Check the phone is still on the car Wi-Fi with Phone MAC selected and VPN off.");
                paragraph("1. Tap Connect to vehicle below. Confirm that you are on the car Wi-Fi.\n2. Wait for Connected.\n3. Tap Register this phone and confirm Register in the prompt.\n4. Wait for the app's registration response, then continue.");
                hint("Registration received is an acknowledgement, not the final test. If pairing expires, go back to Enter pairing mode and repeat it, then reconnect.");
                connectionActions(true);action("Pairing expired — repeat step",false,()->jump(TutorialFlow.PAIR));break;
            case TutorialFlow.OFF_TEST:
                title.setText("Test with the car OFF");testSession=host.session();
                paragraph("1. Tap Disconnect below.\n2. Turn the car fully OFF. From ACC, with your foot off the brake, press POWER twice (ON, then OFF). Check the button light and READY indicator are off.\n3. Take the key with you, get out, close the door and lock the car. Stay nearby.");
                paragraph("4. Keep or rejoin the car Wi-Fi. Tap Connect to vehicle without repeating pairing or registration. A fresh Connected result here is the important check.");
                carOff=check("I have turned the vehicle fully OFF");carOff.setOnCheckedChangeListener((b,c)->refresh());
                connectionActions(false);next.setText("Connection works");
                action("It only connects in pairing mode",false,()->jump(TutorialFlow.TROUBLE));break;
            case TutorialFlow.DAILY:
                title.setText("You're ready for everyday use");
                paragraph("For future use: disable your VPN, join the car Wi-Fi and tap Connect to vehicle. You should not need ACC, key-fob presses or Register this phone again.");
                paragraph("Controls work within the car's Wi-Fi range. For climate, leave the vehicle OFF with doors, bonnet and boot closed. Green means the vehicle reports the feature active; a received request alone is not proof.");
                paragraph("Timetables use the vehicle clock. Check a scheduled run in the car before relying on it. Climate demist status does not confirm the electrical heated-windscreen elements.");
                hint("Need this again? Settings → Tutorial. The guide will not open automatically on later launches.");break;
            case TutorialFlow.TROUBLE:
                title.setText("Before resetting anything");
                paragraph("First check VPN off, car Wi-Fi still connected despite no internet, and Phone MAC unchanged. If Android denies network access, use Settings → Check phone Wi-Fi access. A registration reset cannot fix phone-side network blocking.");
                paragraph("Connecting only in pairing mode can mean this phone was not saved. Full registration slots can also prevent adding it. Neither symptom proves a reset is needed. A missing Wi-Fi name can instead be the vehicle's battery-protection sleep.");
                paragraph("If an older registered phone still works, use its original app to remove its own registration. Otherwise, the optional full-reset guide explains how to clear every old phone and start again.");
                action("Copy diagnostics",false,host::diagnostics);action("Official Mitsubishi help (online)",false,this::official);
                next.setText("Review full reset");break;
            case TutorialFlow.RESET_WARNING:
                title.setText("Full reset — read first");
                paragraph("This erases ALL phone registrations and charge/climate timers. Wi-Fi credentials revert to the registration card; the network stops broadcasting until registration setup restarts.");
                hint("The app only shows instructions. You perform the reset with the car's controls. Skip it if normal connection already works.");
                CheckBox card=check("I have the vehicle's Wi-Fi registration card");CheckBox loss=check("I accept losing all saved phones and timer settings");
                CompoundButton.OnCheckedChangeListener consent=(b,c)->{flow.resetConsent(card.isChecked(),loss.isChecked());refresh();};card.setOnCheckedChangeListener(consent);loss.setOnCheckedChangeListener(consent);
                flow.resetConsent(false,false);break;
            case TutorialFlow.RESET_STEPS:
                title.setText("Clear the old registrations");
                paragraph("Park in P, apply the parking brake, sit inside with the key and close the driver's door. Start from power OFF.");
                paragraph("1. Switch hazard lights ON.\n2. Select ACC as before. Within 10 seconds, alternate LOCK/UNLOCK 10 presses total.\n3. After the entry beeps, within five minutes alternate another 20 presses total (ten pairs).\n4. One completion beep confirms deletion.\n5. Switch hazards OFF and power fully OFF.");
                hint("The extra 20 presses are after the initial 10. If no completion beep is heard, stop and check the official guide before assuming success. Never leave the car in ACC/ON.");
                action("Official reset instructions (online)",false,this::official);break;
            case TutorialFlow.RESET_DONE:
                title.setText("Register again from the start");
                paragraph("Keep hazards OFF for normal pairing. Re-enter pairing mode, rejoin Wi-Fi using the card and check Phone MAC again. Then connect and register in Open PHEV, followed by the car-OFF connection test.");
                paragraph("If the stored Wi-Fi password no longer matches, forget only the vehicle network in Android and rejoin it using the card. Forgetting it can reset the MAC preference, so check Phone MAC afterwards.");
                hint("Open PHEV has no Cancel VIN Registration setting. You do not need to uninstall it. Timers must be recreated after the reset.");break;
        }
        if(step>0)action("Restart tutorial",false,()->{flow.restart();render();});
        scroll.post(()->scroll.scrollTo(0,0));refresh();title.announceForAccessibility(title.getText());
    }
    public void refresh(){
        if(!showing())return;
        if(status!=null)status.setText((host.connected()?"Connected to vehicle":"Not connected")+"\n"+host.message());
        if(connect!=null)connect.setEnabled(!host.busy()&&!host.connected());
        if(register!=null)register.setEnabled(host.connected()&&!host.busy());
        if(disconnect!=null)disconnect.setEnabled(host.connected()||host.busy());
        next.setEnabled(flow.canContinue()&&(flow.step()!=TutorialFlow.REGISTER||!host.busy())&&(flow.step()!=TutorialFlow.OFF_TEST||carOff!=null&&TutorialFlow.offTestConfirmed(carOff.isChecked(),host.connected(),testSession,host.session())));
    }
}
