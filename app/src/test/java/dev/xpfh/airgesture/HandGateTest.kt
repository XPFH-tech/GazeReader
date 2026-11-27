package dev.xpfh.airgesture
import org.junit.Test
import org.junit.Assert.*
class HandGateTest {
    private fun sample(g:HandGate,t:Long,p:Pose=Pose.OPEN,x:Float=.5f,y:Float=.5f)=g.sample(HandFrame(t,p,x,y),t)
    private fun arm(g:HandGate){for(t in 100L..800L step 100)assertNull(sample(g,t))}
    @Test fun openThenSwipe(){val g=HandGate();arm(g);assertEquals(Action.RIGHT,sample(g,950,x=.8f));assertNull(sample(g,1050,x=.9f))}
    @Test fun noUnarmedAction(){val g=HandGate();assertNull(sample(g,100));assertNull(sample(g,200,x=.9f));assertNull(sample(g,300,Pose.FIST))}
    @Test fun diagonalRejected(){val g=HandGate();arm(g);assertNull(sample(g,950,x=.8f,y=.8f))}
    @Test fun screenshotExplicit(){val g=HandGate(screenshot=true);arm(g);for(t in 900L..1500L step 100)assertNull(sample(g,t,Pose.FIST));assertEquals(Action.SHOT,sample(g,1600,Pose.FIST))}
    @Test fun screenshotDefaultOff(){val g=HandGate();arm(g);for(t in 900L..1800L step 100)assertNull(sample(g,t,Pose.FIST))}
    @Test fun gapDisarms(){val g=HandGate();arm(g);assertNull(sample(g,1500,x=.9f))}
    @Test fun staleDisarms(){val g=HandGate();arm(g);assertNull(g.sample(HandFrame(900,Pose.OPEN,.9f,.5f),1501))}
    @Test fun fourDirections(){for((a,xy) in listOf(Action.LEFT to (.2f to .5f),Action.RIGHT to (.8f to .5f),Action.UP to (.5f to .2f),Action.DOWN to (.5f to .8f))){val g=HandGate();arm(g);assertEquals(a,sample(g,950,x=xy.first,y=xy.second))}}
}
