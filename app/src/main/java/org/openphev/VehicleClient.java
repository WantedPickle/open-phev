// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class VehicleClient implements AutoCloseable {
    public interface Listener { void state(String message); void register(int number,byte[] value); void lost(String reason); }
    private final Socket socket;
    private final Listener listener;
    private final Protocol protocol=new Protocol();
    private final TimerSession timers=new TimerSession();
    private final ChargeSession chargeTimers=new ChargeSession();
    private final CountDownLatch handshake=new CountDownLatch(1), vin=new CountDownLatch(1);
    private final Map<Integer,CompletableFuture<byte[]>> pending=new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed=false, ready=false;
    private volatile String failureReason;
    private volatile long lastReceive=System.nanoTime();
    private int sequence=10;
    private long startedAt;
    private volatile long receivedBytes;
    private volatile long sentBytes;
    private volatile int receivedFrames, sentFrames, sentPings;
    private volatile long lastCommandAt,lastHandshakeAt;
    private volatile int lastCommandRegister=-1,handshakeCount;
    private volatile String chargePlug="not received",chargeStatus="not received";
    private final java.util.concurrent.atomic.AtomicBoolean failureReported=new java.util.concurrent.atomic.AtomicBoolean();
    public VehicleClient(Socket socket,Listener listener) { this.socket=socket;this.listener=listener; }
    public void start() throws IOException {
        startedAt=System.nanoTime();
        lastReceive=startedAt;
        socket.setSoTimeout(30000); socket.setTcpNoDelay(true);
        // MY18 vehicle initiates security. Do not send the older plaintext F2/F6-AA preamble.
        // A short listen-first window distinguishes a pre-command close from rejection of a ping.
        listener.state("MY18 startup: listening first; no identity/open preamble. First periodic keepalive after 2 seconds if idle.");
        heartbeat.scheduleAtFixedRate(()->{
            if(closed)return;
            try {if(System.nanoTime()-lastReceive>500_000_000L){sendPing();}}
            catch(Exception e){fail(e.getMessage());}
        },2000,500,TimeUnit.MILLISECONDS);
        Thread reader=new Thread(this::readLoop,"phev-reader"); reader.setDaemon(true); reader.start();
    }
    public void awaitReady() throws Exception {
        if(!handshake.await(20,TimeUnit.SECONDS)||!ready||closed) throw new IOException(failureReason!=null?failureReason:"No MY18 handshake. Saved vehicle registration is not verified; copy diagnostics.");
    }
    public void registerPhone() throws Exception {
        awaitReady();
        if(!vin.await(15,TimeUnit.SECONDS)||closed)throw new IOException("Vehicle identity not received; registration not sent.");
        write(0x10,new byte[]{1});
    }
    public synchronized void write(int reg,byte[] value) throws Exception {
        if(!ready||closed)throw new IOException("Connect to the vehicle first");
        CompletableFuture<byte[]> result=new CompletableFuture<>();
        pending.put(reg,result);
        try { listener.state(String.format(Locale.ROOT,"TX command register %02x payload=%s",reg,Protocol.hex(value)));send(0xf6,0,reg,value);byte[] response=result.get(10,TimeUnit.SECONDS);listener.state(String.format(Locale.ROOT,"RX acknowledgement register %02x payload=%s",reg,Protocol.hex(response)));if(response.length!=1||response[0]!=0)throw new IOException("Vehicle did not confirm acceptance (response "+Protocol.hex(response)+")"); }
        catch(TimeoutException e) { fail("Command acknowledgement timed out; reconnect before retrying"); throw new IOException("No acknowledgement. Outcome unknown; check vehicle before retrying."); }
        finally {pending.remove(reg);}
    }
    private final Object sendLock=new Object();
    public synchronized boolean saveClimateTimer(byte[] original,int originalMode,byte[] requested)throws Exception{
        if(!ready||closed)throw new IOException("Reconnect before editing timers; nothing was written.");
        TimerSession.Plan plan=timers.prepare(original,originalMode,requested);
        listener.state("Timer preflight: current-session settings checked; schedule change="+plan.scheduleChanged+", mode change="+plan.modeChanged);
        if(plan.unchanged()){listener.state("Timer already matches vehicle-reported settings; no write sent");return false;}
        try{
            write(0x1a,plan.write);
            timers.await(plan,8000);
        }catch(Exception e){throw new IOException("Timer save not confirmed. "+e.getMessage(),e);}
        listener.state("Timer save confirmed: changed settings reported by vehicle; unchanged settings retained from this session");
        return true;
    }
    public synchronized boolean saveChargeTimer(byte[] original,int originalSetting,byte[] requested)throws Exception{
        if(!ready||closed)throw new IOException("Reconnect before editing charging schedules; nothing was written.");
        ChargeSession.Plan plan=chargeTimers.prepare(original,originalSetting,requested);
        listener.state("Charging schedule preflight: schedule change="+plan.scheduleChanged+", shared setting change="+plan.modeChanged);
        if(plan.unchanged()){listener.state("Charging schedule already matches; no write sent");return false;}
        try{write(0x19,plan.write);chargeTimers.await(plan,8000);}
        catch(Exception e){throw new IOException("Charging schedule save not confirmed. "+e.getMessage(),e);}
        listener.state("Charging schedule confirmed by vehicle notifications");return true;
    }
    private void sendPing() throws IOException {
        synchronized(sendLock){send(0xf3,0,sequence++%100,new byte[]{0});if(++sentPings==1)listener.state("First keepalive sent after "+((System.nanoTime()-startedAt)/1_000_000L)+" ms");}
    }
    private void send(int type,int ack,int reg,byte[] data) throws IOException {
        synchronized(sendLock) {
            if(closed)throw new IOException("Disconnected");
            if(type==0xf6&&ack==0){lastCommandRegister=reg;lastCommandAt=System.nanoTime();}
            byte[] packet=protocol.encode(type,ack,reg,data);
            socket.getOutputStream().write(packet);
            sentBytes+=packet.length;sentFrames++;
            socket.getOutputStream().flush();
        }
    }
    private void readLoop() {
        try {
            Protocol.Decoder decoder=new Protocol.Decoder();byte[] b=new byte[2048];int n;
            while(!closed&&(n=socket.getInputStream().read(b))!=-1) {
                if(receivedBytes==0)listener.state("First vehicle bytes received after "+((System.nanoTime()-startedAt)/1_000_000L)+" ms");
                receivedBytes+=n;
                lastReceive=System.nanoTime();
                for(Protocol.Frame f:decoder.feed(b,n)) {
                    receivedFrames++;
                    if(f.type!=0x3f)listener.state(Diagnostics.frame(f));
                    if(f.type==0x5e) {
                        synchronized(sendLock) {protocol.initialize(f.raw);send(0xe5,1,1,new byte[]{0});}
                        handshakeCount++;lastHandshakeAt=System.nanoTime();
                        ready=true;handshake.countDown();listener.state("Vehicle handshake received (MY18)");
                    } else if(f.type==0x4e||f.type==0x6e)throw new IOException("This alpha supports the MY18 protocol only");
                    else if(f.type==0x2f){listener.state("Vehicle keepalive request received");sendPing();}
                    else if(f.type==0xbb||f.type==0xcc)throw new IOException("Vehicle rejected session encoding; reconnect");
                    else if(f.type==0x6f) {
                        if(f.ack==0) {
                            send(0xf6,1,f.register,new byte[]{0});
                            if(f.register==0x15&&f.data.length==20)vin.countDown();
                            timers.received(f.register,f.data);
                            chargeTimers.received(f.register,f.data);
                            if(f.register==0x1e&&f.data.length==2)chargePlug=Protocol.hex(f.data);
                            if(f.register==0x1f&&f.data.length==3)chargeStatus=Protocol.hex(f.data);
                            listener.register(f.register,f.data.clone());
                        } else {
                            if(f.register==0xaa)listener.state("Vehicle session-open acknowledgement received");
                            CompletableFuture<byte[]> p=pending.get(f.register);if(p!=null)p.complete(f.data.clone());
                        }
                    }
                }
            }
            if(!closed)fail("Vehicle closed the connection");
        } catch(Exception e) {if(!closed)fail(e.getMessage());}
    }
    private void fail(String reason){if(!failureReported.compareAndSet(false,true))return;failureReason=reason==null?"Connection failed":reason;listener.state("Session ended after "+((System.nanoTime()-startedAt)/1_000_000L)+" ms; received bytes="+receivedBytes+", decoded frames="+receivedFrames+", transmitted bytes="+sentBytes+", transmitted frames="+sentFrames+", keepalives="+sentPings+", security handshake="+ready);listener.state("Session context: handshakes="+handshakeCount+", last handshake age ms="+(lastHandshakeAt==0?"none":(System.nanoTime()-lastHandshakeAt)/1_000_000L)+", last command="+(lastCommandRegister<0?"none":String.format(Locale.ROOT,"%02x",lastCommandRegister))+", command age ms="+(lastCommandAt==0?"none":(System.nanoTime()-lastCommandAt)/1_000_000L)+", last reported charge plug="+chargePlug+", charge status="+chargeStatus+" (raw status; no automatic command retry)");if(receivedBytes==0&&sentBytes==0)listener.state("Connection ended before any application bytes were exchanged. No session, registration or control command was sent.");close();listener.lost(failureReason);}
    @Override public void close() {
        closed=true;ready=false;heartbeat.shutdownNow();handshake.countDown();vin.countDown();
        timers.close();
        chargeTimers.close();
        for(CompletableFuture<byte[]> p:pending.values())p.completeExceptionally(new IOException("Disconnected"));
        try{socket.close();}catch(IOException ignored){}
    }
}
