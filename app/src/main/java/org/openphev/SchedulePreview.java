// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Read-only forecasts from the vehicle's clock, never phone alarms or proof of execution. */
public final class SchedulePreview {
    private SchedulePreview() {}
    public static LocalDateTime clock(byte[] value,long received,long now) {
        if(value==null||value.length!=7||now<received||now-received>LiveStatus.FRESH_NS)return null;
        try {return LocalDateTime.of(2000+(value[0]&255),value[1]&255,value[2]&255,
            value[3]&255,value[4]&255,0).plusNanos(now-received);}
        catch(DateTimeException e){return null;}
    }
    public static boolean editable(byte[] read,int slot,boolean charge) {
        if(read==null||read.length!=(charge?20:16))return false;
        if(charge)return ChargeTimers.validTimes(read,slot)&&ChargeTimers.days(read,slot)!=0
            &&(read[slot*4+3]&3)>=1&&(read[slot*4+3]&3)<=2
            &&(ChargeTimers.startHour(read,slot)!=ChargeTimers.endHour(read,slot)||ChargeTimers.startMinute(read,slot)!=ChargeTimers.endMinute(read,slot));
        int v=ClimateTimers.value(read,slot),flag=(v>>17)&3;
        return ((v>>12)&31)<24&&((v>>9)&7)<6&&(v&3)<3&&((v>>2)&127)!=0&&(flag==1||flag==2);
    }
    public static boolean enabled(byte[] read,int slot,boolean charge) {
        return editable(read,slot,charge)&&(charge?ChargeTimers.enabled(read,slot):((ClimateTimers.value(read,slot)>>17)&3)==1);
    }
    public static byte[] toggle(byte[] read,int slot,boolean charge,int setting) {
        if(!editable(read,slot,charge))throw new IllegalArgumentException("Set a valid time and repeat days first");
        boolean on=!enabled(read,slot,charge);
        if(charge){
            if(setting<0||setting>3)throw new IllegalArgumentException("Reload the charging setting first");
            byte[] out=java.util.Arrays.copyOf(read,21);out[slot*4+3]=(byte)((out[slot*4+3]&0xfc)|(on?1:2));out[20]=(byte)Math.max(2,setting);return out;
        }
        if(setting<1||setting>3)throw new IllegalArgumentException("Choose the shared climate mode first");
        byte[] out=new byte[16];System.arraycopy(read,1,out,0,15);out[15]=(byte)setting;
        out[slot*3+2]=(byte)((out[slot*3+2]&0xf9)|(on?2:4));return out;
    }
    public static final class Next {
        public final LocalDateTime time; public final int slot;
        Next(LocalDateTime time,int slot){this.time=time;this.slot=slot;}
        public String describe(LocalDateTime now){long days=java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(),time.toLocalDate());
            String day=days==0?"Today":days==1?"Tomorrow":time.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.UK));
            return day+" · "+time.format(DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT))+" · Timer "+(slot+1);}
    }
    public static Next next(byte[] read,boolean charge,int setting,LocalDateTime now) {
        if(now==null||read==null||read.length!=(charge?20:16)||(charge?(setting!=2&&setting!=3):(setting<1||setting>3)))return null;
        Next best=null;
        for(int slot=0;slot<5;slot++){
            if(!enabled(read,slot,charge))continue;
            int v=charge?0:ClimateTimers.value(read,slot),days=charge?ChargeTimers.days(read,slot):(v>>2)&127;
            int hour=charge?ChargeTimers.startHour(read,slot):(v>>12)&31,minute=charge?ChargeTimers.startMinute(read,slot):((v>>9)&7)*10;
            for(int d=0;d<=7;d++){
                LocalDate date=now.toLocalDate().plusDays(d);if((days&(1<<(date.getDayOfWeek().getValue()%7)))==0)continue;
                LocalDateTime candidate=date.atTime(hour,minute);if(candidate.isBefore(now))continue;
                if(best==null||candidate.isBefore(best.time))best=new Next(candidate,slot);break;
            }
        }
        return best;
    }
}
