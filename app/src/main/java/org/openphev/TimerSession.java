// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.io.IOException;

/** Vehicle-reported settings for ONE TCP session. No ACK or local edit enters this cache. */
public final class TimerSession {
    private byte[] schedule,mode;
    private long scheduleRevision,modeRevision;
    private boolean closed;
    public static final class Plan {
        final byte[] write;
        final boolean scheduleChanged,modeChanged;
        final long scheduleRevision,modeRevision;
        Plan(byte[] write,boolean scheduleChanged,boolean modeChanged,long sr,long mr){this.write=write.clone();this.scheduleChanged=scheduleChanged;this.modeChanged=modeChanged;scheduleRevision=sr;modeRevision=mr;}
        public boolean unchanged(){return !scheduleChanged&&!modeChanged;}
    }
    public synchronized void received(int register,byte[] data){
        if(closed)return;
        if(register==5){schedule=data.clone();scheduleRevision++;}
        else if(register==0x1c){mode=data.clone();modeRevision++;}
        else return;
        notifyAll();
    }
    private void requireOpen()throws IOException{if(closed)throw new IOException("Connection ended; timer save is not confirmed.");}
    public synchronized Plan prepare(byte[] original,int originalMode,byte[] write)throws IOException{
        requireOpen();
        if(schedule==null||schedule.length!=16||mode==null||mode.length!=1)throw new IOException("Timer settings are incomplete. Reconnect and wait for the schedule and mode; nothing was written.");
        if(!ClimateTimers.unchanged(original,originalMode,schedule,mode))throw new IOException("Vehicle schedule or mode changed while editing. Nothing was written. Reopen the editor to review the latest settings.");
        if(write==null||write.length!=16||(write[15]&255)<1||(write[15]&255)>3)throw new IOException("Invalid timer request; nothing was written.");
        boolean changed=false;for(int i=0;i<15;i++)changed|=write[i]!=schedule[i+1];
        return new Plan(write,changed,ClimateTimers.mode(mode)!=(write[15]&255),scheduleRevision,modeRevision);
    }
    public synchronized boolean confirmed(Plan plan){
        return !closed&&ClimateTimers.matches(plan.write,schedule,mode)
            &&(!plan.scheduleChanged||scheduleRevision>plan.scheduleRevision)
            &&(!plan.modeChanged||modeRevision>plan.modeRevision);
    }
    public synchronized void await(Plan plan,long timeoutMillis)throws IOException,InterruptedException{
        long end=System.nanoTime()+timeoutMillis*1_000_000L;
        while(!confirmed(plan)){
            requireOpen();long remaining=end-System.nanoTime();
            if(remaining<=0)throw new IOException("Timer command was acknowledged, but changed settings were not confirmed (schedule="+(!plan.scheduleChanged||scheduleRevision>plan.scheduleRevision)+", mode="+(!plan.modeChanged||modeRevision>plan.modeRevision)+"). Reconnect and check the stored schedule before retrying.");
            wait(Math.max(1,remaining/1_000_000L));
        }
    }
    public synchronized void close(){closed=true;schedule=null;mode=null;notifyAll();}
}
