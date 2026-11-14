// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

/** Connection may start once, after fresh callbacks for the selected network.
 * Kept independent of Android so callback ordering/cancellation can be tested. */
public final class NetworkGate {
    private final long expected;
    private boolean available, capabilities, link, unblocked, claimed, cancelled;
    public NetworkGate(long expected){this.expected=expected;}
    public boolean matches(long network){return network==expected;}
    public synchronized void available(long n){if(matches(n)&&!cancelled)available=true;}
    public synchronized void capabilities(long n,boolean usable){if(matches(n)&&!cancelled)capabilities=usable;}
    public synchronized void link(long n,boolean vehicleSubnet){if(matches(n)&&!cancelled)link=vehicleSubnet;}
    public synchronized void blocked(long n,boolean blocked){if(matches(n)&&!cancelled)unblocked=!blocked;}
    public synchronized boolean claim(){if(cancelled||claimed||!available||!capabilities||!link||!unblocked)return false;claimed=true;return true;}
    public synchronized void cancel(){cancelled=true;}
}
