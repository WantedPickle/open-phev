/* SPDX-License-Identifier: GPL-3.0-or-later
 * Protocol/key schedule adapted from Ben Buxton's phev2mqtt (2021).
 * See THIRD_PARTY.md. No Mitsubishi application code is included.
 */
package org.openphev;

import java.io.*;
import java.util.*;

public final class Protocol {
    public static final class Frame {
        public final int type, ack, register;
        public final byte[] data, raw;
        Frame(byte[] b) {
            raw=b; type=b[0]&255; ack=b[2]&255; register=b[3]&255;
            data=Arrays.copyOfRange(b,4,b.length-1);
        }
    }
    private int[] keys;
    private int sendIndex;
    public synchronized void initialize(byte[] raw) throws IOException {
        if(raw.length<12) throw new IOException("Short security handshake");
        int seed=0;
        for(int i=0;i<8;i++) seed|=((raw[i+4]>>3)&1)<<i;
        keys=new int[256];
        for(int i=0;i<256;i++) keys[i]=i;
        int j=0;
        for(int i=0;i<256;i++) { j=(j+keys[i]+seed)&255; int t=keys[i]; keys[i]=keys[j]; keys[j]=t; }
        sendIndex=0;
    }
    public synchronized byte[] encode(int type,int ack,int reg,byte[] data) {
        if(data.length>252) throw new IllegalArgumentException("Frame too large");
        byte[] b=new byte[data.length+5];
        b[0]=(byte)type; b[1]=(byte)(b.length-2); b[2]=(byte)ack; b[3]=(byte)reg;
        System.arraycopy(data,0,b,4,data.length);
        int sum=0; for(int i=0;i<b.length-1;i++) sum+=b[i]&255;
        b[b.length-1]=(byte)sum;
        boolean plain=type==0xe5||type==0xe4||type==0xe6;
        int key=plain||keys==null?0:keys[sendIndex];
        if(type==0xf6&&keys!=null) sendIndex=(sendIndex+1)&255;
        for(int i=0;i<b.length;i++) b[i]^=(byte)key;
        return b;
    }
    // Streaming decoder: retains partial packets and handles coalesced TCP reads.
    // ACK is 0/1, allowing the XOR byte to be recovered and checksum validated.
    public static final class Decoder {
        private byte[] buffer=new byte[0];
        private static boolean known(int t) { return t==0xf2||t==0x5e||t==0x4e||t==0x6e||t==0x6f||t==0x3f||t==0x2f||t==0xbb||t==0xcc||t==0xf6||t==0xf3||t==0xe5; }
        public List<Frame> feed(byte[] bytes,int n) throws IOException {
            if(n<0||n>bytes.length||buffer.length+n>8192) throw new IOException("Invalid receive buffer");
            int old=buffer.length; buffer=Arrays.copyOf(buffer,old+n); System.arraycopy(bytes,0,buffer,old,n);
            List<Frame> result=new ArrayList<>();
            while(buffer.length>=3) {
                boolean incomplete=false; byte[] valid=null;
                for(int ack=0;ack<=1;ack++) {
                    int key=(buffer[2]&255)^ack, type=(buffer[0]&255)^key;
                    int len=((buffer[1]&255)^key)+2;
                    if(!known(type)||len<5||len>257) continue;
                    if(buffer.length<len) { incomplete=true; continue; }
                    byte[] candidate=Arrays.copyOf(buffer,len); int sum=0;
                    for(int i=0;i<len;i++) { candidate[i]^=(byte)key; if(i<len-1) sum+=candidate[i]&255; }
                    if((sum&255)==(candidate[len-1]&255)) {valid=candidate;break;}
                }
                if(valid==null) {
                    if(incomplete) break;
                    throw new IOException("Malformed vehicle packet / checksum mismatch");
                }
                result.add(new Frame(valid)); buffer=Arrays.copyOfRange(buffer,valid.length,buffer.length);
            }
            return result;
        }
    }
    public static byte[] climate(int mode,int minutes,int delay) {
        if(mode<1||mode>3||!(minutes==10||minutes==20||minutes==30)||!(delay==0||delay==5||delay==10)) throw new IllegalArgumentException("Invalid climate setting");
        return new byte[]{2,(byte)mode,(byte)(minutes/10-1),(byte)(delay/5)};
    }
    public static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString(); }
}
