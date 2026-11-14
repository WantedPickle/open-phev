// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ProtocolTest {
    static int checks=0;
    static void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
    static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
    static void equal(byte[] b,String expected){check(Protocol.hex(b).equals(expected),"Expected "+expected+", got "+Protocol.hex(b));}
    static byte[] frame(int type,int ack,int reg,byte[] data){return new Protocol().encode(type,ack,reg,data);}
    public static void main(String[] args)throws Exception {
        equal(new Protocol().encode(0xf6,0,6,new byte[]{3}),"f60400060303");
        byte[] capture=hex("ff879094eda82091132d9091ece0a891906f6f93906f6f93c8");
        for(int split=0;split<=capture.length;split++){
            Protocol.Decoder d=new Protocol.Decoder();List<Protocol.Frame> f=new ArrayList<>();f.addAll(d.feed(Arrays.copyOf(capture,split),split));byte[] rest=Arrays.copyOfRange(capture,split,capture.length);f.addAll(d.feed(rest,rest.length));
            check(f.size()==1,"One frame at TCP split "+split);equal(f.get(0).raw,"6f1700047d38b00183bd00017c70380100ffff0300ffff0358");
        }
        Protocol.Decoder combined=new Protocol.Decoder();byte[] pair=hex("06f4f0f6f3f306f4f0f6f3f3");check(combined.feed(pair,pair.length).size()==2,"Coalesced frames");
        byte[] bad=hex("f60400060304");boolean rejected=false;try{new Protocol.Decoder().feed(bad,bad.length);}catch(IOException e){rejected=true;}check(rejected,"Reject invalid checksum");
        Protocol key=new Protocol();key.initialize(hex("5e0c0001becfe9adada5158b0181"));
        byte[] first=key.encode(0xf6,0,6,new byte[]{3});check((first[0]&255)==(0xf6^246),"Upstream first key vector");
        for(int i=1;i<255;i++)key.encode(0xf6,0,6,new byte[]{3});byte[] last=key.encode(0xf6,0,6,new byte[]{3});check((last[0]&255)==(0xf6^164),"Upstream last key vector");check(Arrays.equals(first,key.encode(0xf6,0,6,new byte[]{3})),"Send key wraps after 256");
        equal(Protocol.climate(3,10,0),"02030000");equal(Protocol.climate(2,20,5),"02020101");
        boolean invalid=false;try{Protocol.climate(4,10,0);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"Reject unsupported mode");
        byte[] schedule=hex("01a5f0fe0102030405060708090a0b0c"); // 16 bytes incl header
        check(schedule.length==16,"Timer fixture length");
        byte[] updated=ClimateTimers.update(schedule,1,7,30,20,62,true,3);
        byte[] restored=ClimateTimers.expectedRead(updated,schedule[0]);
        for(int i=0;i<16;i++)if(i<4||i>6)check(restored[i]==schedule[i],"Preserve unrelated schedule byte "+i);
        int v=ClimateTimers.value(restored,1);check(((v>>12)&31)==7&&((v>>9)&7)==3&&((v>>2)&127)==62,"Timer fields");
        timerModes();
        rejectedAcknowledgements();
        integration();
        reconnect();
        diagnosticPrivacy();
        rejectedSession(false);
        rejectedSession(true);
        cancelledStartup();
        networkCallbacks();
        LiveStatusTest.run();
        TimerSessionTest.run();
        ChargeTimersTest.run();
        SchedulePreviewTest.run();
        TutorialFlowTest.run();
        System.out.println("PASS: "+checks+" checks (capture vectors, fragmentation, checksum, rolling keys, climate, timer preservation, TCP session/registration/ACK).");
    }
    static void timerModes(){
        byte[] original=hex("a5000000000000000000000000000000");
        for(int mode=1;mode<=3;mode++){
            byte[] write=ClimateTimers.update(original,0,6,50,20,62,true,mode);
            check((write[15]&255)==mode,"Shared mode is final write byte, never copied header");
            equal(Arrays.copyOf(write,3),"f96a02");
            byte[] read=ClimateTimers.expectedRead(write,(byte)0x7f);
            check(ClimateTimers.matches(write,read,new byte[]{(byte)mode}),"Timer read-back accepts independent header");
            check(!ClimateTimers.matches(write,read,new byte[]{(byte)(mode%3+1)}),"Mode mismatch cannot confirm save");
            read[9]=1;check(!ClimateTimers.matches(write,read,new byte[]{(byte)mode}),"Other slot mismatch cannot confirm save");
        }
        check(ClimateTimers.modeName(1).equals("Cool cabin")&&ClimateTimers.modeName(2).equals("Heat cabin")&&ClimateTimers.modeName(3).contains("Demist"),"Explicit mode names");
        boolean rejected=false;try{ClimateTimers.update(original,0,6,45,20,62,true,2);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("10-minute");}check(rejected,"06:45 rejected with useful interval explanation");
        rejected=false;try{ClimateTimers.update(original,0,6,50,20,62,true,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Unknown mode requires explicit choice");
        check(ClimateTimers.unchanged(original,2,original.clone(),new byte[]{2}),"Unchanged refreshed schedule can be written");
        check(!ClimateTimers.unchanged(original,2,original,new byte[]{3}),"Concurrent shared-mode change blocks stale save");
        byte[] changed=original.clone();changed[8]=1;check(!ClimateTimers.unchanged(original,2,changed,new byte[]{2}),"Concurrent schedule change blocks stale save");
        check(!ClimateTimers.matches(new byte[16],new byte[15],new byte[]{0}),"Malformed read-back cannot confirm save");
    }
    static void rejectedAcknowledgements()throws Exception{
        for(byte[] reply:new byte[][]{new byte[]{1},new byte[0],new byte[]{0,0}}){
            try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
                server.setSoTimeout(5000);ExecutorService executor=Executors.newSingleThreadExecutor();
                Future<?> car=executor.submit(()->{try(Socket peer=server.accept()){
                    peer.setSoTimeout(5000);OutputStream out=peer.getOutputStream();out.write(hex("5e0c0001becfe9adada5158b0181"));out.flush();
                    Protocol.Decoder decoder=new Protocol.Decoder();byte[] buffer=new byte[1024];boolean done=false;
                    while(!done){int n=peer.getInputStream().read(buffer);if(n<0)throw new IOException("Premature EOF");for(Protocol.Frame f:decoder.feed(buffer,n))if(f.type==0xf6&&f.ack==0){out.write(frame(0x6f,1,f.register,reply));out.flush();done=true;}}
                    Thread.sleep(100);
                }catch(Exception e){throw new RuntimeException(e);}});
                VehicleClient client=new VehicleClient(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),new VehicleClient.Listener(){public void state(String s){}public void register(int r,byte[] b){}public void lost(String s){}});
                try{client.start();client.awaitReady();boolean rejected=false;try{client.write(0x0a,new byte[]{1});}catch(IOException e){rejected=e.getMessage().contains("did not confirm acceptance");}check(rejected,"Reject unsuccessful/malformed acknowledgement "+Protocol.hex(reply));car.get(5,TimeUnit.SECONDS);}finally{client.close();executor.shutdownNow();}
            }
        }
    }
    static void networkCallbacks(){
        NetworkGate gate=new NetworkGate(267);
        check(!gate.claim(),"No socket before callbacks");
        gate.available(265);gate.capabilities(265,true);gate.link(265,true);gate.blocked(265,false);
        check(!gate.claim(),"Stale network callbacks cannot authorize socket");
        gate.available(267);gate.capabilities(267,true);gate.link(267,true);
        check(!gate.claim(),"Must wait for explicit blocked status");
        gate.blocked(267,true);check(!gate.claim(),"Blocked network cannot authorize socket");
        gate.blocked(267,false);check(gate.claim(),"All selected network callbacks authorize socket");
        check(!gate.claim(),"Repeated callbacks cannot open duplicate socket");
        NetworkGate cancelled=new NetworkGate(267);cancelled.available(267);cancelled.capabilities(267,true);cancelled.link(267,true);cancelled.blocked(267,false);cancelled.cancel();check(!cancelled.claim(),"Cancellation defeats late callbacks");
        // Exercise every permutation of the four readiness callbacks.
        for(int a=0;a<4;a++)for(int b=0;b<4;b++)for(int c=0;c<4;c++)for(int d=0;d<4;d++){
            if(a==b||a==c||a==d||b==c||b==d||c==d)continue;
            NetworkGate order=new NetworkGate(1);int[] events={a,b,c,d};
            for(int i=0;i<4;i++){switch(events[i]){case 0:order.available(1);break;case 1:order.capabilities(1,true);break;case 2:order.link(1,true);break;case 3:order.blocked(1,false);break;}check(order.claim()==(i==3),"Callback ordering readiness "+a+b+c+d+" step "+i);}
        }
        NetworkGate wrongLink=new NetworkGate(1);wrongLink.available(1);wrongLink.capabilities(1,true);wrongLink.blocked(1,false);wrongLink.link(1,false);check(!wrongLink.claim(),"Reject different subnet");wrongLink.link(1,true);wrongLink.capabilities(1,false);check(!wrongLink.claim(),"Reject non-Wi-Fi/VPN capabilities");
    }
    static void diagnosticPrivacy(){
        byte[] identity=hex("0156313233343536373839414243444546470102");
        for(int reg:new int[]{6,0x15}){
            String log=Diagnostics.frame(new Protocol.Frame(frame(0x6f,0,reg,identity)));
            check(log.contains("metadata[0]=01 [18]=01 [19]=02"),"Registration metadata retained");
            check(!log.contains("563132")&&!log.contains("V123"),"VIN omitted from diagnostics");
            check(!log.contains("clients="),"Metadata not labelled as registration count");
        }
        byte[] secret=hex("5e0c0001becfe9adada5158b0181");
        check(!Diagnostics.frame(new Protocol.Frame(secret)).contains("becfe9"),"Security material omitted");
        String session=Diagnostics.frame(new Protocol.Frame(frame(0xf2,0,1,hex("02112233445500"))));
        check(!session.contains("021122")&&session.contains("payload omitted"),"Session MAC omitted");
        check(Diagnostics.frame(new Protocol.Frame(frame(0x2f,1,1,new byte[]{0}))).contains("response=00"),"Session reply code retained");
        check(Diagnostics.frame(new Protocol.Frame(frame(0x6f,0,0x1e,new byte[]{1,0}))).contains("charge status bytes=0100"),"Allowlist charge plug status");
        check(Diagnostics.frame(new Protocol.Frame(frame(0x6f,0,0x1f,new byte[]{1,0,0}))).contains("charge status bytes=010000"),"Allowlist charging status");
    }
    static void rejectedSession(boolean afterPing)throws Exception{
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> car=executor.submit(()->{try(Socket peer=server.accept()){
                if(afterPing){peer.setSoTimeout(5000);byte[] ping=new byte[6];new DataInputStream(peer.getInputStream()).readFully(ping);equal(ping,"f304000a0001");}
                else{peer.setSoTimeout(250);try{int unexpected=peer.getInputStream().read();throw new AssertionError("Unexpected startup byte/EOF: "+unexpected);}catch(SocketTimeoutException expected){check(true,"No application bytes during listen-first window");}}
            }catch(Exception e){throw new RuntimeException(e);}});
            List<String> log=new CopyOnWriteArrayList<>();
            VehicleClient client=new VehicleClient(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),new VehicleClient.Listener(){public void state(String s){log.add(s);}public void register(int r,byte[] v){}public void lost(String r){}});
            try{client.start();boolean failed=false;try{client.awaitReady();}catch(IOException e){failed=true;check(e.getMessage().contains("Vehicle closed"),"Preserve remote-close reason");}check(failed,"Remote close never reports ready");check(log.stream().anyMatch(s->s.contains("received bytes=0, decoded frames=0")),"No-response diagnostic retained");check(log.stream().anyMatch(s->s.contains(afterPing?"transmitted bytes=6, transmitted frames=1":"transmitted bytes=0, transmitted frames=0")),"Distinguish pre-send and post-ping remote close");check(log.stream().anyMatch(s->s.contains("before any application bytes"))==!afterPing,"No false claim of zero transmission");car.get(5,TimeUnit.SECONDS);}finally{client.close();executor.shutdownNow();}
        }
    }
    static void cancelledStartup()throws Exception{
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);
            Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
            try(Socket peer=server.accept()){
                peer.setSoTimeout(5000);VehicleClient client=new VehicleClient(socket,new VehicleClient.Listener(){public void state(String s){}public void register(int r,byte[] v){}public void lost(String r){}});
                try{client.start();client.close();check(peer.getInputStream().read()==-1,"Cancel startup closes socket without sending preamble or ping");boolean failed=false;try{client.awaitReady();}catch(IOException e){failed=true;}check(failed,"Cancelled startup cannot become ready");}finally{client.close();}
            }
        }
    }
    static void reconnect()throws Exception {
        // Two ordinary sessions: only a keepalive is allowed before vehicle-led security.
        // Exact wire fixtures catch plaintext preambles and premature/incorrect command encoding.
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> car=executor.submit(()->{try{
                for(int session=0;session<2;session++)try(Socket peer=server.accept()){
                    peer.setSoTimeout(5000);DataInputStream in=new DataInputStream(peer.getInputStream());byte[] ping=new byte[6];in.readFully(ping);
                    equal(ping,"f304000a0001");
                    OutputStream out=peer.getOutputStream();out.write(hex("5e0c0001becfe9adada5158b0181"));out.flush();
                    byte[] securityAck=new byte[6];in.readFully(securityAck);equal(securityAck,"e504010100eb");
                    Protocol.Decoder decoder=new Protocol.Decoder();byte[] b=new byte[6];boolean done=false;
                    while(!done){in.readFully(b);for(Protocol.Frame f:decoder.feed(b,b.length)){
                        check(f.type!=0xf2,"No identity preamble in MY18 session");
                        if(f.type==0xf6&&f.ack==0){check(f.register!=0x10,"Normal reconnect must not register again");check(f.register==0x0a,"Expected headlight command, no AA open write");equal(Arrays.copyOf(b,6),"00f2f6fcf7f3");out.write(frame(0x6f,1,f.register,new byte[]{0}));out.flush();done=true;}
                    }}
                    Thread.sleep(50);
                }
            }catch(Exception e){throw new RuntimeException(e);}});
            try{for(int i=0;i<2;i++){
                VehicleClient client=new VehicleClient(new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()),new VehicleClient.Listener(){public void state(String s){}public void register(int r,byte[] v){}public void lost(String r){}});
                try{client.start();client.awaitReady();client.write(0x0a,new byte[]{1});}finally{client.close();}
            }car.get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
        }
    }
    static void integration()throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(5000);
            ExecutorService exec=Executors.newSingleThreadExecutor();
            Future<?> emulation=exec.submit(()->{try(Socket peer=server.accept()){
                peer.setSoTimeout(5000);OutputStream out=peer.getOutputStream();
                out.write(hex("5e0c0001becfe9adada5158b0181"));out.flush();
                Protocol.Decoder decoder=new Protocol.Decoder();byte[] buf=new byte[1024];boolean registered=false,climate=false,identitySent=false;
                while(!climate){int n=peer.getInputStream().read(buf);if(n<0)throw new IOException("Premature EOF");for(Protocol.Frame f:decoder.feed(buf,n)){
                    if(f.type==0xe5){check(f.ack==1&&f.register==1,"Handshake ACK");out.write(frame(0x6f,0,0x15,new byte[20]));out.flush();identitySent=true;}
                    if(f.type==0xf6&&f.ack==0){
                        if(f.register==0x10){check(identitySent,"Identity before registration");equal(f.data,"01");registered=true;}
                        if(f.register==0x1b){check(registered,"Registration before climate test");equal(f.data,"02030000");climate=true;}
                        out.write(frame(0x6f,1,f.register,new byte[]{0}));out.flush();
                    }
                }}
                Thread.sleep(100);
            }catch(Exception e){throw new RuntimeException(e);}});
            Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
            VehicleClient client=new VehicleClient(socket,new VehicleClient.Listener(){public void state(String s){}public void register(int r,byte[] v){}public void lost(String r){}});
            try{client.start();client.awaitReady();client.registerPhone();client.write(0x1b,Protocol.climate(3,10,0));emulation.get(10,TimeUnit.SECONDS);}finally{client.close();exec.shutdownNow();}
        }
    }
}
