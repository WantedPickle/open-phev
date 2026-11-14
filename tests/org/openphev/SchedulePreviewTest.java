// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;
import java.time.*;
import java.util.Arrays;
import static org.openphev.ProtocolTest.check;

public final class SchedulePreviewTest {
    static byte[] climate(byte[] read,int slot,int hour,int minute,int days,boolean on){return ClimateTimers.expectedRead(ClimateTimers.update(read,slot,hour,minute,20,days,on,3),read[0]);}
    public static void run(){
        byte[] empty=ProtocolTest.hex("0100fe0700fe0700fe0700fe0700fe07");
        LocalDateTime fri=LocalDateTime.of(2026,11,13,6,0);
        byte[] c=climate(empty,0,6,40,62,true);
        SchedulePreview.Next n=SchedulePreview.next(c,false,3,fri);
        check(n!=null&&n.slot==0&&n.time.equals(fri.withMinute(40)),"Next climate on same day");
        check(n.describe(fri).contains("Today · 06:40"),"24-hour next display");
        check(SchedulePreview.next(c,false,3,fri.withHour(7)).time.equals(LocalDateTime.of(2026,11,16,6,40)),"Weekdays skip weekend");
        byte[] earlier=climate(c,4,6,10,62,true);check(SchedulePreview.next(earlier,false,3,fri).slot==4,"Earliest time wins rather than slot order");
        check(SchedulePreview.next(c,false,0,fri)==null,"Unknown mode cannot predict climate");
        check(SchedulePreview.next(empty,false,3,fri)==null,"Empty sentinel never predicts run");
        byte[] midnight=climate(empty,2,0,0,1,true);
        check(SchedulePreview.next(midnight,false,3,LocalDateTime.of(2026,11,14,23,59)).time.equals(LocalDateTime.of(2026,11,15,0,0)),"Sunday midnight next day");
        check(SchedulePreview.next(midnight,false,3,LocalDateTime.of(2026,11,15,0,1)).time.equals(LocalDateTime.of(2026,11,22,0,0)),"Already passed weekly start rolls seven days");
        byte[] off=SchedulePreview.toggle(c,0,false,3),offRead=ClimateTimers.expectedRead(off,c[0]);
        check(!SchedulePreview.enabled(offRead,0,false),"Toggle disables climate");
        check(SchedulePreview.next(offRead,false,3,fri)==null,"Disabled climate excluded");
        byte[] again=ClimateTimers.expectedRead(SchedulePreview.toggle(offRead,0,false,3),c[0]);check(Arrays.equals(c,again),"Climate on/off roundtrip preserves every byte");
        for(int i=0;i<c.length;i++)if(i!=3)check(c[i]==offRead[i],"Climate toggle changes only flag byte "+i);
        check(off[15]==3,"Toggle retains shared demist mode");
        byte[] unusual=c.clone();unusual[3]|=(byte)0xf8;byte[] toggled=ClimateTimers.expectedRead(SchedulePreview.toggle(unusual,0,false,2),unusual[0]);
        check((toggled[3]&0xf9)==(unusual[3]&0xf9),"Toggle preserves unknown climate bits");
        boolean blocked=false;try{SchedulePreview.toggle(empty,0,false,3);}catch(IllegalArgumentException e){blocked=true;}check(blocked,"Empty slot must be edited first");
        byte[] charge=ProtocolTest.hex("7d083d0100ffff0300ffff0300ffff0300ffff03");
        n=SchedulePreview.next(charge,true,2,fri);check(n!=null&&n.time.equals(LocalDateTime.of(2026,11,16,1,0)),"Charging predicts next start, not today's end");
        byte[] cd=SchedulePreview.toggle(charge,0,true,3);check(cd[20]==3,"Location restriction preserved on toggle");
        for(int i=0;i<20;i++)if(i!=3)check(cd[i]==charge[i],"Charge toggle preserves other byte "+i);
        check(!ChargeTimers.enabled(Arrays.copyOf(cd,20),0),"Charge toggle disables");
        check(Arrays.equals(charge,Arrays.copyOf(SchedulePreview.toggle(Arrays.copyOf(cd,20),0,true,3),20)),"Charge toggle roundtrip exact");
        check(SchedulePreview.next(charge,true,0,fri)==null,"Inactive shared charging setting excluded");
        check(SchedulePreview.toggle(charge,0,true,0)[20]==2,"Inactive shared charging default matches editor");
        byte[] overnight=Arrays.copyOf(ChargeTimers.update(charge,0,23,0,7,0,32,true,false,2),20);
        check(SchedulePreview.next(overnight,true,2,fri).time.equals(fri.withHour(23)),"Overnight repeats on start day");
        byte[] clock={26,11,13,6,0,0,5};long received=1_000_000_000L;
        check(SchedulePreview.clock(clock,received,received+30_000_000_000L).equals(fri.plusSeconds(30)),"Vehicle clock advances monotonically");
        check(SchedulePreview.clock(clock,received,received+121_000_000_000L)==null,"Stale clock hides forecast");
        check(SchedulePreview.clock(clock,received,received-1)==null,"Negative clock age rejected");
        check(SchedulePreview.clock(new byte[]{26,2,30,6,0,0,0},0,0)==null,"Invalid calendar date rejected");
        check(SchedulePreview.clock(new byte[]{28,2,29,23,59,0,0},0,60_000_000_000L).equals(LocalDateTime.of(2028,3,1,0,0)),"Leap-day rollover");
        check(SchedulePreview.clock(null,0,0)==null,"Missing clock is not phone clock");
    }
}
