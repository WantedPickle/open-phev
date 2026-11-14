// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class TimerSessionTest {
    private static void check(boolean value,String label){ProtocolTest.check(value,label);}
    private static byte[] schedule(){return new byte[16];}
    private static TimerSession session(byte[] read){TimerSession s=new TimerSession();s.received(5,read);s.received(0x1c,new byte[]{1});return s;}
    public static void run()throws Exception{
        byte[] original=schedule(),write=ClimateTimers.update(original,0,6,40,10,62,true,1);
        TimerSession s=session(original);TimerSession.Plan p=s.prepare(original,1,write);
        s.received(0x12,new byte[7]);s.received(0x1d,new byte[4]);
        check(!s.confirmed(p),"Clock/battery refresh cannot confirm changed timers");
        boolean timeout=false;try{s.await(p,1);}catch(IOException e){timeout=e.getMessage().contains("not confirmed");}check(timeout,"ACK without timer notification remains unconfirmed");
        s.received(5,ClimateTimers.expectedRead(write,(byte)0));
        check(s.confirmed(p),"Schedule-only change does not require unchanged mode to be re-emitted");
        TimerSession.Plan noop=s.prepare(ClimateTimers.expectedRead(write,(byte)0),1,write);
        check(noop.unchanged()&&s.confirmed(noop),"Identical settings need no write");
        s=session(original);byte[] modeOnly=new byte[16];modeOnly[15]=3;p=s.prepare(original,1,modeOnly);
        check(!s.confirmed(p),"Changed mode needs an actual notification");s.received(0x1c,new byte[]{3});check(s.confirmed(p),"Mode-only change does not require unchanged schedule to be re-emitted");
        write[15]=3;s=session(original);p=s.prepare(original,1,write);s.received(0x1c,new byte[]{3});check(!s.confirmed(p),"Both changed: mode alone insufficient");s.received(5,ClimateTimers.expectedRead(write,(byte)0));check(s.confirmed(p),"Both changes confirmed in either order");
        s.received(5,new byte[15]);check(!s.confirmed(p),"Malformed schedule cannot confirm");s.close();check(!s.confirmed(p),"Disconnected settings cannot confirm");
        boolean closed=false;try{s.await(p,100);}catch(IOException e){closed=true;}check(closed,"Disconnect ends wait immediately");
        TimerSession empty=new TimerSession();boolean missing=false;try{empty.prepare(original,1,write);}catch(IOException e){missing=e.getMessage().contains("incomplete");}check(missing,"A new connection cannot reuse old settings");
        s=session(original);s.received(0x1c,new byte[]{2});boolean conflict=false;try{s.prepare(original,1,write);}catch(IOException e){conflict=e.getMessage().contains("changed while editing");}check(conflict,"Latest notification blocks conflicting edit");
        s=session(original);p=s.prepare(original,1,write);write[0]++;s.received(5,ClimateTimers.expectedRead(p.write,(byte)0));s.received(0x1c,new byte[]{3});check(s.confirmed(p),"Plan owns a copy of the reviewed write");
        for(int scenario=0;scenario<4;scenario++)socketSave(scenario);
    }
    private static void socketSave(int scenario)throws Exception{
        byte[] original=schedule();int mode=scenario==0?1:3;
        byte[] write=scenario==1?new byte[16]:ClimateTimers.update(original,0,6,40,10,62,true,mode);write[15]=(byte)mode;
        AtomicInteger writes=new AtomicInteger(),refreshes=new AtomicInteger();CountDownLatch initial=new CountDownLatch(1);
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> car=executor.submit(()->{try(Socket peer=server.accept()){
                peer.setSoTimeout(5000);OutputStream out=peer.getOutputStream();out.write(ProtocolTest.hex("5e0c0001becfe9adada5158b0181"));out.flush();
                Protocol.Decoder decoder=new Protocol.Decoder();byte[] b=new byte[1024];int n;
                while((n=peer.getInputStream().read(b))!=-1)for(Protocol.Frame f:decoder.feed(b,n)){
                    if(f.type==0xe5){out.write(ProtocolTest.frame(0x6f,0,5,original));out.write(ProtocolTest.frame(0x6f,0,0x1c,new byte[]{1}));out.flush();}
                    if(f.type==0xf6&&f.ack==0){
                        if(f.register==6){refreshes.incrementAndGet();out.write(ProtocolTest.frame(0x6f,1,6,new byte[]{0}));out.write(ProtocolTest.frame(0x6f,0,0x12,new byte[7]));out.flush();}
                        else if(f.register==0x1a){
                            writes.incrementAndGet();ProtocolTest.equal(f.data,Protocol.hex(write));
                            if(scenario==3){out.write(ProtocolTest.frame(0xbb,1,0xb3,new byte[]{0}));out.flush();continue;}
                            // Test notifications both before and after the command ACK.
                            if(scenario==1)out.write(ProtocolTest.frame(0x6f,0,0x1c,new byte[]{3}));
                            out.write(ProtocolTest.frame(0x6f,1,0x1a,new byte[]{0}));
                            if(scenario!=1)out.write(ProtocolTest.frame(0x6f,0,5,ClimateTimers.expectedRead(write,(byte)0)));
                            if(scenario==2)out.write(ProtocolTest.frame(0x6f,0,0x1c,new byte[]{3}));out.flush();
                        }else throw new AssertionError("Unexpected timer-flow write "+f.register);
                    }
                }
            }catch(Exception e){throw new RuntimeException(e);}});
            java.util.List<String> logs=new CopyOnWriteArrayList<>();
            VehicleClient client=new VehicleClient(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),new VehicleClient.Listener(){public void state(String s){logs.add(s);}public void lost(String s){}public void register(int r,byte[] b){if(r==0x1c)initial.countDown();}});
            try{
                client.start();client.awaitReady();check(initial.await(3,TimeUnit.SECONDS),"Initial timer snapshot received");
                client.write(6,new byte[]{3}); // Reproduce the owner's refresh: no repeated timers or mode.
                if(scenario==3){boolean rejected=false;try{client.saveClimateTimer(original,1,write);}catch(IOException e){rejected=e.getMessage().contains("not confirmed");}check(rejected,"Encoding rejection never confirms timer save");check(logs.stream().anyMatch(s->s.contains("Session context:")&&s.contains("last command=1a")),"Disconnect records command context without key material");}
                else{check(client.saveClimateTimer(original,1,write),"Save succeeds despite partial refresh; scenario "+scenario);
                check(!client.saveClimateTimer(ClimateTimers.expectedRead(write,(byte)0),mode,write),"Repeated identical Save sends nothing");}
            }finally{client.close();try{car.get(5,TimeUnit.SECONDS);}finally{executor.shutdownNow();}}
            check(writes.get()==1,"Exactly one timer write; no automatic retry");check(refreshes.get()==1,"Save does not send extra refresh requests");
        }
    }
}
