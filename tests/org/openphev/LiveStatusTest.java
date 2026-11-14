// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.util.HashMap;
import java.util.Map;

final class LiveStatusTest {
    private static final long SECOND=1_000_000_000L;
    private final Map<Integer,byte[]> data=new HashMap<>();
    private final Map<Integer,Long> times=new HashMap<>();
    private final LiveStatus status=new LiveStatus();
    private long now=SECOND;
    private void put(int reg,String value){data.put(reg,ProtocolTest.hex(value));times.put(reg,now);}
    private LiveStatus.Indicator view(int feature,int target){return status.indicator(feature,target,true,data,times,now);}
    private void tone(int feature,int target,LiveStatus.Tone expected,String why){ProtocolTest.check(view(feature,target).tone==expected,why);}
    static void run(){new LiveStatusTest().scenario();}
    private void scenario(){
        final int climate=LiveStatus.CLIMATE,head=LiveStatus.HEADLIGHTS,park=LiveStatus.PARKING;
        final LiveStatus.Tone green=LiveStatus.Tone.ACTIVE,amber=LiveStatus.Tone.WAITING,grey=LiveStatus.Tone.NEUTRAL;
        tone(climate,2,grey,"No green when disconnected data is absent");
        put(0x10,"000000");put(0x1c,"00");
        tone(climate,2,grey,"Idle climate is not heating");
        now+=SECOND;status.begin(climate,2,0,now);tone(climate,2,amber,"Heat shows sending");
        status.acknowledged(climate);tone(climate,2,amber,"Heat ACK alone does not prove activation");
        now+=SECOND;put(0x10,"027022");tone(climate,2,amber,"Running without heat mode is insufficient");
        put(0x1c,"02");tone(climate,2,green,"User log running plus heat mode confirms heat");tone(climate,3,grey,"Other modes remain off");
        now+=SECOND;status.begin(climate,3,0,now);status.acknowledged(climate);put(0x10,"027022");
        tone(climate,3,amber,"Mode switch ACK and running do not imply windscreen");put(0x1c,"03");tone(climate,3,green,"Windscreen becomes green from mode 03");tone(climate,2,grey,"Heat clears when windscreen is confirmed");
        now+=SECOND;status.begin(climate,1,0,now);put(0x1c,"01");tone(climate,1,amber,"Status before ACK does not prematurely complete pending request");status.acknowledged(climate);tone(climate,1,amber,"Mode alone does not reuse a pre-request running sample");put(0x10,"027022");tone(climate,1,green,"Cooling confirmed with both fresh status fields");tone(climate,3,grey,"Windscreen clears on cooling");
        now+=SECOND;status.begin(climate,0,0,now);status.acknowledged(climate);tone(climate,0,amber,"Stop ACK waits for stopped status");tone(climate,1,green,"Actual cooling remains visible until stop confirmed");put(0x10,"000000");tone(climate,0,grey,"Stop confirmed");tone(climate,1,grey,"Old mode 01 cannot leave cooling active after stop");
        now+=SECOND;status.begin(climate,2,5,now);status.acknowledged(climate);tone(climate,2,amber,"Delayed request is not active");ProtocolTest.check(view(climate,2).text.contains("5 min"),"Delay is labelled as requested");
        now+=300*SECOND;put(0x10,"027022");put(0x1c,"02");tone(climate,2,green,"Delayed request only becomes active after actual status");
        now+=LiveStatus.FRESH_NS+1;tone(climate,2,grey,"Stale status cannot remain green");
        put(0x10,"027022");tone(climate,2,grey,"Fresh running with stale mode remains unknown");put(0x1c,"02");tone(climate,2,green,"Fresh refresh restores confirmed status");
        ProtocolTest.check(status.indicator(climate,2,false,data,times,now).tone==grey,"Disconnect hides green even with cached data");
        now+=SECOND;status.begin(climate,2,0,now);status.acknowledged(climate);tone(climate,2,amber,"Repeated request cannot reuse pre-request active samples");
        now+=16*SECOND;ProtocolTest.check(view(climate,2).text.contains("Not confirmed"),"Unconfirmed immediate request has visible timeout state");
        status.failed(climate);tone(climate,2,amber,"Failed request is not green");ProtocolTest.check(view(climate,2).text.contains("failed"),"Failure is explicit");
        status.clear();put(0x1c,"07");tone(climate,2,grey,"Unknown mode is never green");put(0x10,"010000");put(0x1c,"02");tone(climate,2,grey,"Non-running climate state is not treated as active");put(0x10,"030000");tone(climate,2,grey,"Vehicle terminated state turns indicator off");
        put(0x24,"02000000000000000001");tone(head,1,green,"Headlight indicator comes from actual lamp byte");
        now+=SECOND;status.begin(head,0,0,now);tone(head,1,amber,"Light off request is visible on light row");status.acknowledged(head);tone(head,1,amber,"Light off ACK does not imply lamp switched off");put(0x24,"02000000000000000000");tone(head,1,grey,"Lamp off read-back clears indicator");
        put(0x1d,"18000103");tone(park,1,green,"Parking light status uses battery-register byte 2");
        now+=SECOND;status.begin(park,0,0,now);status.acknowledged(park);tone(park,1,amber,"Parking off ACK waits for status");put(0x1d,"18000003");tone(park,1,grey,"Parking off status clears green");
        put(0x24,"02000000000000000002");tone(head,1,grey,"Unknown headlight byte is not silently on");put(0x1d,"180001");tone(park,1,grey,"Malformed parking register is unknown");
        put(0x10,"027022");put(0x1c,"02");times.put(0x10,now+SECOND);tone(climate,2,grey,"Future timestamps cannot appear fresh");
        status.begin(climate,2,0,now);status.acknowledged(climate);status.begin(climate,3,0,now);ProtocolTest.check(view(climate,3).text.equals("Sending…"),"New request does not inherit previous acknowledgement");status.clear();
    }
}
