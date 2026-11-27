package dev.xpfh.airgesture
import org.junit.Test
import org.junit.Assert.*
class HandGateTest{
 private val p=mapOf(Action.UP to Profile(.08f,-1,.35f),Action.DOWN to Profile(.10f),Action.PALM_BACK to Profile(.45f,1,.07f),Action.BACK_PALM to Profile(.45f,-1,.07f),Action.LEFT to Profile(.10f,1,.40f),Action.RIGHT to Profile(.10f,-1,.40f),Action.SHOT to Profile(3f))
 private fun f(t:Long,x:Float=.5f,y:Float=.5f,face:Float=.85f,straight:Int=4,curled:Int=0,finger:Float=-.8f,tip:Float=.3f,fx:Float=0f)=HandFrame(t,if(curled>=3)Pose.FIST else Pose.OPEN,x,y,face,straight,curled,.16f,finger,tip,fx)
 private fun arm(g:HandGate,face:Float=.85f,finger:Float=-.8f,tip:Float=.3f){for(t in 100L..500L step 100)assertNull(g.sample(f(t,face=face,finger=finger,tip=tip),t))}
 @Test fun wristUp(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);assertNull(g.sample(f(600,face=.02f,finger=.05f,tip=.5f),600));assertNull(g.sample(f(700,face=.8f,finger=-.8f,tip=.3f),700));assertEquals(Action.UP,g.sample(f(800,face=.8f,finger=-.8f,tip=.28f),800))}
 @Test fun noUpWithoutFaceFlip(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,face=-.85f,finger=-.8f,tip=.3f),t))}
 @Test fun noUpWithoutFingerFlip(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,face=.85f,finger=.8f,tip=.5f),t))}
 @Test fun noUpWithoutTipTravel(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,face=.85f,finger=-.8f,tip=.69f),t))}
 @Test fun down(){val g=HandGate(p);arm(g);assertNull(g.sample(f(600,y=.72f),600));assertEquals(Action.DOWN,g.sample(f(700,y=.75f),700))}
 @Test fun horizontalIgnored(){val g=HandGate(p);arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,x=.8f),t))}
 @Test fun palmBack(){val g=HandGate(p);arm(g);assertNull(g.sample(f(600,face=.05f),600));assertNull(g.sample(f(700,face=-.8f),700));assertNull(g.sample(f(800,face=-.8f),800));assertNull(g.sample(f(900,face=-.8f),900));assertEquals(Action.PALM_BACK,g.sample(f(1000,face=-.8f),1000))}
 @Test fun backPalm(){val g=HandGate(p);arm(g,-.85f);g.sample(f(600,face=.05f),600);g.sample(f(700,face=.8f),700);g.sample(f(800,face=.8f),800);assertNull(g.sample(f(900,face=.8f),900));assertEquals(Action.BACK_PALM,g.sample(f(1000,face=.8f),1000))}
 @Test fun fist(){val g=HandGate(p,true);arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,straight=0,curled=4),t));assertEquals(Action.SHOT,g.sample(f(1100,straight=0,curled=4),1100))}
 @Test fun fistDisabled(){val g=HandGate(p);arm(g);for(t in 600L..1300L step 100)assertNull(g.sample(f(t,straight=0,curled=4),t))}
 @Test fun missingProfile(){val g=HandGate(emptyMap());arm(g);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,y=.2f),t))}
 @Test fun gapDisarms(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);assertNull(g.sample(f(1200,face=.8f,tip=.3f),1200))}
 @Test fun calibrationUp(){val c=Calibration(Action.UP,0);for(t in 1500L..1800L step 100)c.sample(f(t,face=-.85f,straight=2,finger=.8f,tip=.7f),t);c.sample(f(1900,face=.8f,straight=2,finger=-.8f,tip=.3f),1900);c.sample(f(2000,face=.8f,straight=2,finger=-.8f,tip=.28f),2000);assertEquals(-1,c.result?.profile?.faceSign);assertTrue(c.result!!.profile!!.threshold in .018f.. .22f)}
 @Test fun calibrationWrongStart(){val c=Calibration(Action.UP,0);for(t in 1500L..2200L step 100)c.sample(f(t,face=-.85f),t);assertNull(c.result)}
 @Test fun calibrationFlip(){val c=Calibration(Action.PALM_BACK,0);for(t in 1500L..2000L step 100)c.sample(f(t),t);for(t in 2100L..2400L step 100)c.sample(f(t,face=-.8f),t);assertEquals(1,c.result?.profile?.faceSign)}
 @Test fun wristUpNoHorizontalFallthrough(){val g=HandGate(p);arm(g,-.85f,.8f,.7f);for(t in 600L..1000L step 100)assertNull(g.sample(f(t,face=.8f,finger=.8f,tip=.7f),t))}
 private fun horizontalArm(g:HandGate,face:Float){for(t in 100L..500L step 100)assertNull(g.sample(f(t,face=face,finger=.05f,fx=.9f),t))}
 @Test fun leftFlip(){val g=HandGate(p);horizontalArm(g,.85f);assertNull(g.sample(f(600,x=.40f,face=.05f,finger=.05f,fx=.9f),600));for(t in 700L..800L step 100)assertNull(g.sample(f(t,x=.28f,face=-.8f,finger=.05f,fx=.9f),t));assertEquals(Action.LEFT,g.sample(f(900,x=.28f,face=-.8f,finger=.05f,fx=.9f),900))}
 @Test fun rightFlip(){val g=HandGate(p);horizontalArm(g,-.85f);g.sample(f(600,x=.60f,face=.05f,finger=.05f,fx=.9f),600);for(t in 700L..800L step 100)assertNull(g.sample(f(t,x=.72f,face=.8f,finger=.05f,fx=.9f),t));assertEquals(Action.RIGHT,g.sample(f(900,x=.72f,face=.8f,finger=.05f,fx=.9f),900))}
 @Test fun horizontalStationaryNoClick(){val g=HandGate(p);horizontalArm(g,-.85f);for(t in 600L..1300L step 100)assertNull(g.sample(f(t,face=.8f,finger=.05f,fx=.9f),t))}
 @Test fun uprightLargeTravelNoBack(){val g=HandGate(p);arm(g);for(t in 600L..1300L step 100)assertNull(g.sample(f(t,x=.25f,face=-.8f),t))}
 @Test fun horizontalCalibration(){val c=Calibration(Action.LEFT,0);for(t in 1500L..2000L step 100)c.sample(f(t,finger=.05f,fx=.9f),t);for(t in 2100L..2400L step 100)c.sample(f(t,x=.28f,face=-.8f,finger=.05f,fx=.9f),t);assertEquals(1,c.result?.profile?.faceSign)}
 @Test fun mappings(){assertEquals("返回",Action.PALM_BACK.output);assertEquals("中心单击",Action.BACK_PALM.output);assertEquals("左滑",Action.LEFT.output);assertEquals("右滑",Action.RIGHT.output)}
}
