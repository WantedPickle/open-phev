// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

public final class ClimateTimers {
    private ClimateTimers(){}
    public static int value(byte[] read,int slot) {
        if(read.length!=16||slot<0||slot>4)throw new IllegalArgumentException("Unsupported timer layout");
        int p=1+slot*3;return (read[p]&255)|((read[p+1]&255)<<8)|((read[p+2]&255)<<16);
    }
    public static String modeName(int mode){switch(mode){case 1:return "Cool cabin";case 2:return "Heat cabin";case 3:return "Demist / defrost windscreen";default:return "Mode not set / unknown";}}
    public static int mode(byte[] status){return status!=null&&status.length==1?(status[0]&15):0;}
    public static byte[] update(byte[] read,int slot,int hour,int minute,int minutes,int days,boolean enabled,int mode) {
        int old=value(read,slot);
        if(mode<1||mode>3)throw new IllegalArgumentException("Choose the climate mode for all timer slots");
        if(minute<0||minute>50||minute%10!=0)throw new IllegalArgumentException("Vehicle timers use 10-minute steps: 00, 10, 20, 30, 40 or 50");
        if(hour<0||hour>23||minute<0||minute>50||minute%10!=0||minutes<10||minutes>30||minutes%10!=0||days<0||days>127||(enabled&&days==0))throw new IllegalArgumentException("Choose time, duration and repeat days");
        int v=(old&0xf80000)|(enabled?1<<17:1<<18)|(hour<<12)|((minute/10)<<9)|(days<<2)|(minutes/10-1);
        // The final write byte is the shared mode, NOT the unknown read header.
        byte[] out=new byte[16];System.arraycopy(read,1,out,0,15);out[15]=(byte)mode;
        int p=slot*3;out[p]=(byte)v;out[p+1]=(byte)(v>>8);out[p+2]=(byte)(v>>16);return out;
    }
    public static byte[] expectedRead(byte[] write,byte header){byte[] b=new byte[16];b[0]=header;System.arraycopy(write,0,b,1,15);return b;}
    public static boolean matches(byte[] write,byte[] read,byte[] modeStatus){
        if(write==null||write.length!=16||read==null||read.length!=16||mode(modeStatus)!=(write[15]&255))return false;
        for(int i=0;i<15;i++)if(write[i]!=read[i+1])return false;return true;
    }
    public static boolean unchanged(byte[] original,int originalMode,byte[] latest,byte[] latestMode){return java.util.Arrays.equals(original,latest)&&originalMode==mode(latestMode);}
    public static String days(int mask){
        if(mask==127)return "Every day";if(mask==62)return "Weekdays";if(mask==65)return "Weekends";if(mask==0)return "No repeat days";
        String[] names={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};StringBuilder text=new StringBuilder();
        for(int i=0;i<7;i++)if((mask&(1<<i))!=0){if(text.length()>0)text.append(' ');text.append(names[i]);}return text.toString();
    }
    public static String describe(byte[] read,int slot) {
        int v=value(read,slot),hour=(v>>12)&31,minute=(v>>9)&7,mask=(v>>2)&127,flag=(v>>17)&3;
        if(hour==31&&minute==7)return "Timer "+(slot+1)+" · Not set";
        if(hour>23||minute>5||(v&3)>2)return "Timer "+(slot+1)+" · Unsupported setting";
        String state=mask==0?"No repeat days":flag==1?"On":flag==2?"Off":"Unknown state";
        return String.format(java.util.Locale.ROOT,"Timer %d · %s · %02d:%02d\n%d min · %s",slot+1,state,hour,minute*10,((v&3)+1)*10,days(mask));
    }
}
