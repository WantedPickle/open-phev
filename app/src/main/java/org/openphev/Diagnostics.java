// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;
import java.util.Locale;

/** Allowlisted diagnostics. Never dump arbitrary frames, VINs, session keys or MAC addresses. */
public final class Diagnostics {
    private Diagnostics(){}
    public static String frame(Protocol.Frame f) {
        String s=String.format(Locale.ROOT,"RX type=%02x ack=%d register=%02x length=%d",f.type,f.ack,f.register,f.data.length);
        if(f.type==0x6f&&f.ack==0&&(f.register==0x06||f.register==0x15)&&f.data.length==20)
            return s+String.format(Locale.ROOT," metadata[0]=%02x [18]=%02x [19]=%02x (VIN omitted; meanings unverified)",f.data[0]&255,f.data[18]&255,f.data[19]&255);
        if(f.type==0x2f&&f.register==1&&f.data.length==1)
            return s+String.format(Locale.ROOT," response=%02x",f.data[0]&255);
        if(f.type==0x6f&&f.ack==0&&f.register==0x2a&&f.data.length==1)
            return s+String.format(Locale.ROOT," status=%02x",f.data[0]&255);
        if(f.type==0x6f&&f.ack==0&&((f.register==0x1e&&f.data.length==2)||(f.register==0x1f&&f.data.length==3)))
            return s+" charge status bytes="+Protocol.hex(f.data);
        return s+" (payload omitted)";
    }
}
