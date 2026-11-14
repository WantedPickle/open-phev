// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.util.HashMap;
import java.util.Map;

/** Presentation state only: acknowledgements never manufacture vehicle state. */
public final class LiveStatus {
    public static final int CLIMATE=0, HEADLIGHTS=1, PARKING=2;
    public static final long FRESH_NS=120_000_000_000L;
    public enum Tone { ACTIVE, WAITING, NEUTRAL }
    public static final class Indicator {
        public final String text; public final Tone tone;
        Indicator(String text,Tone tone){this.text=text;this.tone=tone;}
    }
    private static final class Request {
        int target,delay;long since;boolean acknowledged,failed;
        Request(int target,int delay,long since){this.target=target;this.delay=delay;this.since=since;}
    }
    private final Map<Integer,Request> requests=new HashMap<>();
    public void begin(int feature,int target,int delay,long now){requests.put(feature,new Request(target,delay,now));}
    public void acknowledged(int feature){Request r=requests.get(feature);if(r!=null)r.acknowledged=true;}
    public void failed(int feature){Request r=requests.get(feature);if(r!=null)r.failed=true;}
    public void clear(){requests.clear();}
    public static boolean fresh(Map<Integer,Long> times,int reg,long now){Long t=times.get(reg);return t!=null&&now>=t&&now-t<=FRESH_NS;}
    private static boolean valid(Map<Integer,byte[]> data,Map<Integer,Long> times,int reg,int length,long now){byte[] b=data.get(reg);return b!=null&&b.length==length&&fresh(times,reg,now);}
    private static int observed(int feature,Map<Integer,byte[]> data,Map<Integer,Long> times,long now){
        if(feature==CLIMATE){
            if(!valid(data,times,0x10,3,now))return -1;
            int running=data.get(0x10)[0]&255;
            if(running==0||running==3)return 0;
            if(running!=2||!valid(data,times,0x1c,1,now))return -1;
            int mode=data.get(0x1c)[0]&15;return mode>=1&&mode<=3?mode:-1;
        }
        int reg=feature==HEADLIGHTS?0x24:0x1d, length=feature==HEADLIGHTS?10:4, offset=feature==HEADLIGHTS?9:2;
        if(!valid(data,times,reg,length,now))return -1;
        int value=data.get(reg)[offset]&255;return value==0||value==1?value:-1;
    }
    private static long observedAt(int feature,int value,Map<Integer,Long> times){
        if(feature==CLIMATE){long time=times.get(0x10);return value==0?time:Math.min(time,times.get(0x1c));}
        return times.get(feature==HEADLIGHTS?0x24:0x1d);
    }
    public Indicator indicator(int feature,int target,boolean connected,Map<Integer,byte[]> data,Map<Integer,Long> times,long now){
        if(!connected)return new Indicator("Not connected",Tone.NEUTRAL);
        int value=observed(feature,data,times,now);
        Request r=requests.get(feature);
        if(r!=null&&r.acknowledged&&value==r.target&&observedAt(feature,value,times)>=r.since){requests.remove(feature);r=null;}
        if(r!=null&&(r.target==target||feature!=CLIMATE)){
            if(r.failed)return new Indicator("Request failed · check status",Tone.WAITING);
            if(!r.acknowledged)return new Indicator("Sending…",Tone.WAITING);
            if(r.delay>0)return new Indicator("Delay requested · "+r.delay+" min",Tone.WAITING);
            return new Indicator(now-r.since>15_000_000_000L?"Not confirmed · refresh":"Received · awaiting status",Tone.WAITING);
        }
        if(value<0)return new Indicator("Status unknown · refresh",Tone.NEUTRAL);
        if(target==0)return new Indicator(value==0?"Off · confirmed":"Stop running climate",Tone.NEUTRAL);
        if(value==target)return new Indicator(feature==CLIMATE?"Active · confirmed":"On · confirmed",Tone.ACTIVE);
        return new Indicator("Off",Tone.NEUTRAL);
    }
}
