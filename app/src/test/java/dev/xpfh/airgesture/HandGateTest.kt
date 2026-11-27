package dev.xpfh.airgesture
import org.junit.Test
import org.junit.Assert.*
class HandGateTest{
 private val p=mapOf(Action.UP to Profile(.12f),Action.DOWN to Profile(.10f),Action.PALM_BACK to Profile(.45f,1),Action.BACK_PALM to Profile(.45f,-1),Action.SHOT to Profile(3f))
 private fun f(t:Long,x:Float=.5f,y:Float=.5f,face:Float=.85f,straight:Int=4,curled:Int=0)=HandFrame(t,if(curled>=3)Pose.FIST else Pose.OPEN,x,y,face,straight,curled,.16f)
 private fun arm(g:HandGate,face:Float=.85f){for(t in 100L..500L step 100)assertNull(g.sample(f(t,face=face),t))}
 @Test fun up(){val g=HandGate(p);arm(g);assertNull(g.sample(f(600,y=.28f),600));assertEquals(Action.UP,g.sample(f(700,y=.25f),700))}
 @Test fun down(){val g=HandGate(p);arm(g);assertNull(g.sample(f(600,y=.72f),600));assertEquals(Action.DOWN,g.sample(f(700,y=.75f),700))}
 @Test fun horizontalIgnored(){val g=HandGate(p);arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,x=.8f),t))}
 @Test fun palmBack(){val g=HandGate(p);arm(g);assertNull(g.sample(f(600,face=.05f),600));assertNull(g.sample(f(700,face=-.8f),700));assertNull(g.sample(f(800,face=-.8f),800));assertEquals(Action.PALM_BACK,g.sample(f(900,face=-.8f),900))}
 @Test fun backPalm(){val g=HandGate(p);arm(g,-.85f);g.sample(f(600,face=.05f),600);g.sample(f(700,face=.8f),700);g.sample(f(800,face=.8f),800);assertEquals(Action.BACK_PALM,g.sample(f(900,face=.8f),900))}
 @Test fun fist(){val g=HandGate(p,true);arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,straight=0,curled=4),t));assertEquals(Action.SHOT,g.sample(f(1100,straight=0,curled=4),1100))}
 @Test fun fistDisabled(){val g=HandGate(p);arm(g);for(t in 600L..1300L step 100)assertNull(g.sample(f(t,straight=0,curled=4),t))}
 @Test fun missingProfile(){val g=HandGate(emptyMap());arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,y=.2f),t))}
 @Test fun gapDisarms(){val g=HandGate(p);arm(g);assertNull(g.sample(f(1200,y=.2f),1200))}
 @Test fun calibrationUp(){val c=Calibration(Action.UP,0);for(t in 1500L..2000L step 100)c.sample(f(t),t);for(t in 2100L..2400L step 100)c.sample(f(t,y=.27f),t);assertNotNull(c.result?.profile)}
 @Test fun calibrationFlip(){val c=Calibration(Action.PALM_BACK,0);for(t in 1500L..2000L step 100)c.sample(f(t),t);for(t in 2100L..2400L step 100)c.sample(f(t,face=-.8f),t);assertEquals(1,c.result?.profile?.faceSign)}
}
