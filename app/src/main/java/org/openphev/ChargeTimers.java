// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.util.Arrays;
import java.util.Locale;

/** MY18 register 04 (five 4-byte slots), write 19 (slots + shared setting). */
public final class ChargeTimers {
    private ChargeTimers(){}
    private static int offset(byte[] read,int slot){if(read==null||read.length!=20||slot<0||slot>4)throw new IllegalArgumentException("Unsupported charging schedule");return slot*4;}
    public static int days(byte[] read,int slot){return (read[offset(read,slot)]&255)>>1;}
    public static int startHour(byte[] read,int slot){return (read[offset(read,slot)+1]&255)>>3;}
    public static int startMinute(byte[] read,int slot){return (read[offset(read,slot)+1]&7)*10;}
    public static int endHour(byte[] read,int slot){return (read[offset(read,slot)+2]&255)>>3;}
    public static int endMinute(byte[] read,int slot){return (read[offset(read,slot)+2]&7)*10;}
    public static boolean fullCharge(byte[] read,int slot){return (read[offset(read,slot)]&1)!=0;}
    public static boolean enabled(byte[] read,int slot){return (read[offset(read,slot)+3]&3)==1;}
    public static int setting(byte[] status){return status!=null&&status.length==1?status[0]&15:-1;}
    public static String settingName(int value){return value==3?"Existing location restriction on":value==2?"Location restriction off":value==0||value==1?"No active schedule setting":"Unknown charging setting";}
    public static boolean validTimes(byte[] read,int slot){return startHour(read,slot)<24&&endHour(read,slot)<24&&startMinute(read,slot)<60&&endMinute(read,slot)<60;}
    public static byte[] update(byte[] read,int slot,int sh,int sm,int eh,int em,int days,boolean enabled,boolean full,int currentSetting){
        int p=offset(read,slot);
        if(sh<0||sh>23||eh<0||eh>23||sm<0||sm>50||em<0||em>50||sm%10!=0||em%10!=0)throw new IllegalArgumentException("Choose 24-hour times in 10-minute steps");
        if(sh==eh&&sm==em)throw new IllegalArgumentException("Start and end must differ; equal times do not mean a full day");
        if(days<1||days>127)throw new IllegalArgumentException("Choose at least one repeat day");
        if(currentSetting<0||currentSetting>3)throw new IllegalArgumentException("Unsupported shared charging setting; no write sent");
        byte[] out=Arrays.copyOf(read,21);out[p]=(byte)((days<<1)|(full?1:0));out[p+1]=(byte)((sh<<3)|(sm/10));out[p+2]=(byte)((eh<<3)|(em/10));out[p+3]=(byte)((read[p+3]&0xfc)|(enabled?1:2));
        // Original client uses 2 when creating schedules; retain an existing locator setting 3.
        out[20]=(byte)(currentSetting>=2?currentSetting:2);return out;
    }
    public static boolean matches(byte[] write,byte[] read,byte[] status){return write!=null&&write.length==21&&read!=null&&read.length==20&&Arrays.equals(Arrays.copyOf(write,20),read)&&setting(status)==(write[20]&255);}
    public static String describe(byte[] read,int slot){
        int p=offset(read,slot),sh=startHour(read,slot),sm=startMinute(read,slot),eh=endHour(read,slot),em=endMinute(read,slot);
        if(sh==31&&sm==70&&eh==31&&em==70)return "Timer "+(slot+1)+" · Not set";
        if(!validTimes(read,slot)||sh==eh&&sm==em)return "Timer "+(slot+1)+" · Unsupported time range";
        int flag=read[p+3]&3,mask=days(read,slot);
        String state=mask==0?"No repeat days":flag==1?"On":flag==2?"Off":"Unknown state";
        return String.format(Locale.ROOT,"Timer %d · %s · %02d:%02d–%02d:%02d%s\n%s%s",slot+1,state,sh,sm,eh,em,(eh*60+em<sh*60+sm)?" (+1 day)":"",ClimateTimers.days(mask),fullCharge(read,slot)?" · Continue to full charge":"");
    }
}
