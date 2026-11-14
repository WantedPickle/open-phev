// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;
import static org.openphev.ProtocolTest.check;

public final class TutorialFlowTest {
    public static void run(){
        check(TutorialFlow.firstRun(false,false),"Fresh install starts tutorial");
        check(!TutorialFlow.firstRun(true,false),"Closing or completing prevents repeat auto launch");
        check(!TutorialFlow.firstRun(false,true),"Existing installations do not repeat initial setup on update");
        check(!TutorialFlow.firstRun(true,true),"Known user never auto launches");
        TutorialFlow f=new TutorialFlow(-7);check(f.step()==0,"Corrupt stored step returns to welcome");
        f=new TutorialFlow(100);check(f.step()==0,"Out of bounds stored page returns to welcome");
        f=new TutorialFlow(TutorialFlow.MAC);check(f.step()==TutorialFlow.MAC,"Resume unfinished phone setting step");
        f.next();check(f.step()==TutorialFlow.PAIR,"Phone MAC before pairing");
        f.next();check(f.step()==TutorialFlow.BEEPS,"Beep help before registration");
        f.next();check(f.step()==TutorialFlow.REGISTER,"Registration follows pairing help");
        f.next();check(f.step()==TutorialFlow.OFF_TEST,"Off test follows registration");
        f=new TutorialFlow(TutorialFlow.WIFI);f.wakeWifi();f.next();check(f.step()==TutorialFlow.BEEPS,"Missing Wi-Fi path includes beep explanation");
        f=new TutorialFlow(f.step(),f.returningToWifi());f.next();check(f.step()==TutorialFlow.WIFI,"Missing Wi-Fi returns to Wi-Fi even after recreation");
        f.go(TutorialFlow.PAIR);f.next();f.next();check(f.step()==TutorialFlow.REGISTER,"Normal pairing does not loop to Wi-Fi");
        f.go(TutorialFlow.RESET_WARNING);check(!f.canContinue(),"Reset instructions gated by acknowledgement");
        f.next();check(f.step()==TutorialFlow.RESET_WARNING,"Next cannot bypass reset warning");
        f.resetConsent(true,false);check(!f.canContinue(),"Card alone not sufficient");
        f.resetConsent(false,true);check(!f.canContinue(),"Loss acknowledgement alone not sufficient");
        f.resetConsent(true,true);f.next();check(f.step()==TutorialFlow.RESET_STEPS,"Both acknowledgements show instructions");
        f=new TutorialFlow(f.step());check(f.step()==TutorialFlow.RESET_WARNING&&!f.canContinue(),"Restoration cannot bypass reset acknowledgement");
        f.resetConsent(true,true);f.next();f.back();check(!f.canContinue(),"Going back resets acknowledgement");
        boolean refused=false;try{f.go(TutorialFlow.RESET_STEPS);}catch(IllegalStateException e){refused=true;}check(refused,"Direct reset-page navigation guarded");
        f.resetConsent(true,true);f.next();f.next();check(f.step()==TutorialFlow.RESET_DONE,"Reset instructions lead to new setup");
        f.next();check(f.step()==TutorialFlow.WIFI,"Reset recovery restarts network setup");
        f.wakeWifi();f.restart();check(f.step()==0&&!f.returningToWifi(),"Restart clears branch state");
        check(!TutorialFlow.offTestConfirmed(false,true,1,2),"Connected alone does not confirm car is off");
        check(!TutorialFlow.offTestConfirmed(true,false,1,2),"Car-off checkbox alone insufficient");
        check(!TutorialFlow.offTestConfirmed(true,true,1,1),"Pairing session cannot pass off-test");
        check(TutorialFlow.offTestConfirmed(true,true,1,3),"User-confirmed power off plus fresh connection passes");
    }
}
