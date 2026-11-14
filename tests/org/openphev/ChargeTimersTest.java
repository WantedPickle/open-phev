// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChargeTimersTest {
    private static void check(boolean value,String label){ProtocolTest.check(value,label);}
    private static byte[] fixture(){return ProtocolTest.hex("7d38b00183bd00017c70380200ffff0300ffff03");}
    public static void run()throws Exception{
        byte[] read=fixture();
        check(read.length==20,"Captured charging read length");
        check(ChargeTimers.startHour(read,2)==14&&ChargeTimers.endHour(read,2)==7&&ChargeTimers.days(read,2)==62,"Decode captured timer fields");
        byte[] write=ChargeTimers.update(read,2,14,0,7,0,62,true,false,2);
        ProtocolTest.equal(write,"7d38b00183bd00017c70380100ffff0300ffff0302");
        check(ChargeTimers.fullCharge(read,0)&&!ChargeTimers.fullCharge(read,2),"Full-charge flag decoded independently of days");
        check(ChargeTimers.describe(read,3).equals("Timer 4 · Not set"),"Empty charge slot not 31:70");
        check(ChargeTimers.describe(read,1).contains("23:50–00:00 (+1 day)"),"Midnight and overnight display");
        for(int slot=0;slot<5;slot++){
            byte[] changed=ChargeTimers.update(read,slot,0,0,23,50,127,false,false,3);
            check(changed[20]==3,"Preserve locator setting");
            for(int i=0;i<20;i++)if(i<slot*4||i>=slot*4+4)check(changed[i]==read[i],"Preserve untouched charge slot byte");
            check(!ChargeTimers.enabled(changedRead(changed),slot)&&!ChargeTimers.fullCharge(changedRead(changed),slot),"Explicit disabled/full-charge choices");
        }
        byte[] high=read.clone();high[3]=(byte)0xd1;write=ChargeTimers.update(high,0,1,10,2,20,65,true,true,2);check((write[3]&0xfc)==0xd0,"Preserve unknown state bits");
        check(ChargeTimers.update(read,2,14,0,7,0,62,true,false,0)[20]==2,"New schedule chooses documented default locator-off setting");
        for(int[] bad:new int[][]{{24,0,7,0,62,2},{0,5,7,0,62,2},{0,0,7,60,62,2},{7,0,7,0,62,2},{0,0,7,0,0,2},{0,0,7,0,62,7}}){boolean rejected=false;try{ChargeTimers.update(read,0,bad[0],bad[1],bad[2],bad[3],bad[4],true,false,bad[5]);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject unsupported charging input");}
        write=ChargeTimers.update(read,2,14,0,7,0,62,true,false,2);ChargeSession session=new ChargeSession();session.received(4,read);session.received(0x1b,new byte[]{2});ChargeSession.Plan plan=session.prepare(read,2,write);
        check(!session.confirmed(plan),"A local charge edit is not confirmed");session.received(0x12,new byte[7]);boolean timeout=false;try{session.await(plan,1);}catch(IOException e){timeout=true;}check(timeout,"Partial refresh cannot confirm a charge write");
        session.received(4,changedRead(write));check(session.confirmed(plan),"Unchanged shared setting need not be repeated");
        session.received(4,new byte[19]);check(!session.confirmed(plan),"Malformed charge update cannot confirm");session.close();check(!session.confirmed(plan),"Disconnected charge cache cannot confirm");
        session=new ChargeSession();session.received(4,read);session.received(0x1b,new byte[]{3});boolean conflict=false;try{session.prepare(read,2,write);}catch(IOException e){conflict=true;}check(conflict,"Concurrent shared-setting change blocks charge save");
        byte[] climate=new byte[16];climate[2]=(byte)0xfe;climate[3]=3;check(ClimateTimers.describe(climate,0).equals("Timer 1 · Not set"),"Climate unset sentinel never displays On or 31:70");
        byte[] midnight=ClimateTimers.expectedRead(ClimateTimers.update(climate,0,0,0,10,127,true,2),(byte)0);check(ClimateTimers.describe(midnight,0).contains("00:00")&&ClimateTimers.describe(midnight,0).contains("Every day"),"Climate midnight and everyday labels");
        check(ClimateTimers.days(62).equals("Weekdays")&&ClimateTimers.days(65).equals("Weekends"),"Compact repeat-day labels");
        for(int scenario=0;scenario<4;scenario++)socketSave(scenario);
    }
    private static byte[] changedRead(byte[] write){return Arrays.copyOf(write,20);}
    private static void socketSave(int scenario)throws Exception{
        byte[] original=fixture();int mode=scenario==0?2:3;
        byte[] write=scenario==1?Arrays.copyOf(original,21):ChargeTimers.update(original,2,14,0,7,0,62,true,false,mode);write[20]=(byte)mode;
        CountDownLatch initial=new CountDownLatch(1);AtomicInteger writes=new AtomicInteger(),refreshes=new AtomicInteger();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> car=executor.submit(()->{try(Socket peer=server.accept()){
                peer.setSoTimeout(5000);OutputStream out=peer.getOutputStream();out.write(ProtocolTest.hex("5e0c0001becfe9adada5158b0181"));out.flush();
                Protocol.Decoder decoder=new Protocol.Decoder();byte[] b=new byte[1024];int n;
                while((n=peer.getInputStream().read(b))!=-1)for(Protocol.Frame f:decoder.feed(b,n)){
                    if(f.type==0xe5){out.write(ProtocolTest.frame(0x6f,0,4,original));out.write(ProtocolTest.frame(0x6f,0,0x1b,new byte[]{2}));out.flush();}
                    if(f.type==0xf6&&f.ack==0){
                        if(f.register==6){refreshes.incrementAndGet();out.write(ProtocolTest.frame(0x6f,1,6,new byte[]{0}));out.write(ProtocolTest.frame(0x6f,0,0x12,new byte[7]));out.flush();}
                        else if(f.register==0x19){writes.incrementAndGet();ProtocolTest.equal(f.data,Protocol.hex(write));
                            if(scenario==3){out.write(ProtocolTest.frame(0xbb,1,0xb3,new byte[]{0}));out.flush();continue;}
                            if(scenario==1)out.write(ProtocolTest.frame(0x6f,0,0x1b,new byte[]{3}));
                            out.write(ProtocolTest.frame(0x6f,1,0x19,new byte[]{0}));
                            if(scenario!=1)out.write(ProtocolTest.frame(0x6f,0,4,changedRead(write)));
                            if(scenario==2)out.write(ProtocolTest.frame(0x6f,0,0x1b,new byte[]{3}));out.flush();
                        }else throw new AssertionError("Unexpected charging-flow write "+f.register);
                    }
                }
            }catch(Exception e){throw new RuntimeException(e);}});
            VehicleClient client=new VehicleClient(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),new VehicleClient.Listener(){public void state(String s){}public void lost(String s){}public void register(int r,byte[] b){if(r==0x1b)initial.countDown();}});
            try{client.start();client.awaitReady();check(initial.await(3,TimeUnit.SECONDS),"Initial charging snapshot received");client.write(6,new byte[]{3});
                if(scenario==3){boolean failed=false;try{client.saveChargeTimer(original,2,write);}catch(IOException e){failed=e.getMessage().contains("not confirmed");}check(failed,"Encoding rejection does not confirm charge save");}
                else{check(client.saveChargeTimer(original,2,write),"Charging save verified from changed notifications");check(!client.saveChargeTimer(changedRead(write),mode,write),"Identical charge save does not write");}
            }finally{client.close();try{car.get(5,TimeUnit.SECONDS);}finally{executor.shutdownNow();}}
            check(writes.get()==1&&refreshes.get()==1,"Exactly one charging write; no refresh loop or automatic replay");
        }
    }
}
