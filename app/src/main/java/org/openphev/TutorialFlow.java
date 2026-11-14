// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

/** Navigation and recovery guards only. This class never talks to the vehicle. */
public final class TutorialFlow {
    public static final int WELCOME=0,VPN=1,WIFI=2,MAC=3,PAIR=4,BEEPS=5,REGISTER=6,OFF_TEST=7,DAILY=8,
        TROUBLE=9,RESET_WARNING=10,RESET_STEPS=11,RESET_DONE=12;
    private int step;
    private boolean card,acceptLoss,wakeWifi;
    public TutorialFlow(int saved){step=saved<0||saved>RESET_DONE?WELCOME:saved==RESET_STEPS?RESET_WARNING:saved;}
    public TutorialFlow(int saved,boolean returningToWifi){this(saved);wakeWifi=returningToWifi;}
    public boolean returningToWifi(){return wakeWifi;}
    public static boolean firstRun(boolean seen,boolean usedBefore){return !seen&&!usedBefore;}
    public int step(){return step;}
    public void resetConsent(boolean hasCard,boolean acceptsLoss){card=hasCard;acceptLoss=acceptsLoss;}
    public boolean canContinue(){return step!=RESET_WARNING||(card&&acceptLoss);}
    public void go(int target){
        if(target<0||target>RESET_DONE)throw new IllegalArgumentException("Unknown tutorial page");
        if(target==RESET_STEPS&&!(step==RESET_WARNING&&card&&acceptLoss))throw new IllegalStateException("Review reset consequences first");
        if(target==RESET_WARNING){card=false;acceptLoss=false;}
        step=target;
    }
    public void wakeWifi(){wakeWifi=true;go(PAIR);}
    public void next(){
        if(!canContinue())return;
        if(step==BEEPS&&wakeWifi){wakeWifi=false;go(WIFI);}
        else if(step==RESET_DONE)go(WIFI);
        else if(step<DAILY||step>=TROUBLE&&step<RESET_DONE)go(step+1);
    }
    public void back(){
        if(step==TROUBLE)go(OFF_TEST);
        else if(step==RESET_DONE)go(TROUBLE);
        else if(step>WELCOME)go(step-1);
    }
    public void restart(){wakeWifi=false;card=false;acceptLoss=false;step=WELCOME;}
    public static boolean offTestConfirmed(boolean carOff,boolean connected,int enteredSession,int currentSession){return carOff&&connected&&enteredSession!=currentSession;}
}
