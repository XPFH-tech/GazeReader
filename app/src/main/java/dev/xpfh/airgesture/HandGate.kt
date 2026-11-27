package dev.xpfh.airgesture
import kotlin.math.abs
import kotlin.math.hypot
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

enum class Pose { NONE, OPEN, FIST, OTHER }
enum class Action(val label:String){ LEFT("左滑"),RIGHT("右滑"),UP("上滑"),DOWN("下滑"),SHOT("截屏") }
data class HandFrame(val at:Long,val pose:Pose,val x:Float=0f,val y:Float=0f)
object HandFeatures {
    fun extract(l:List<NormalizedLandmark>,w:Int,h:Int,at:Long):HandFrame {
        if(l.size!=21||w<=0||h<=0)return HandFrame(at,Pose.NONE)
        val scale=minOf(w,h).toFloat()
        fun x(i:Int)=l[i].x()*w/scale
        fun y(i:Int)=l[i].y()*h/scale
        fun d(a:Int,b:Int)=hypot(x(a)-x(b),y(a)-y(b))
        if(l.any{!it.x().isFinite()||!it.y().isFinite()})return HandFrame(at,Pose.NONE)
        if(l.any{it.x() !in -0.05f..1.05f||it.y() !in -0.05f..1.05f})return HandFrame(at,Pose.NONE)
        val width=d(5,17)
        if(width !in .045f..0.75f)return HandFrame(at,Pose.NONE)
        var straight=0;var curled=0
        for(m in intArrayOf(5,9,13,17)){
            val pip=m+1;val tip=m+3
            val a=d(m,pip);val b=d(pip,tip);val c=d(m,tip)
            if(a<.008f||b<.008f)return HandFrame(at,Pose.OTHER)
            val cos=(a*a+b*b-c*c)/(2*a*b)
            if(cos<-.64f&&d(0,tip)>d(0,pip)+width*.18f)straight++
            if(cos>-.30f&&d(0,tip)<d(0,pip)+width*.12f)curled++
        }
        val pose=when{straight==4->Pose.OPEN;curled>=3&&straight==0->Pose.FIST;else->Pose.OTHER}
        val center=intArrayOf(0,5,9,13,17)
        val cx=center.map{x(it)}.average().toFloat()
        val cy=center.map{y(it)}.average().toFloat()
        // Front-facing non-mirrored analysis; mirror X to match the user's screen direction.
        return HandFrame(at,pose,w/scale-cx,cy)
    }
}
class HandGate(private val hold:Long=650L,private val distance:Float=.20f,private val screenshot:Boolean=false){
    var status="张开手掌并停留";private set
    private var last=-1L;private var since=-1L;private var armed=-1L
    private var neutral=-1L;private var cooldown=0L;private var fist=-1L
    private var ax=0f;private var ay=0f;private var needNeutral=false
    fun reset(){last=-1;since=-1;armed=-1;fist=-1;neutral=-1;status="张开手掌并停留"}
    private fun finish(a:Action,t:Long):Action{reset();needNeutral=true;cooldown=t+1500;status="已触发 "+a.label;return a}
    fun sample(f:HandFrame,now:Long):Action?{
        if(f.at<=last)return null
        if(now-f.at !in 0..500||!f.x.isFinite()||!f.y.isFinite()){reset();return null}
        if(last>=0&&f.at-last>500)reset()
        last=f.at
        if(needNeutral){
            status="请移开手掌 · 冷却"
            if(f.pose==Pose.NONE||f.pose==Pose.OTHER){if(neutral<0)neutral=now;if(now-neutral>=400&&now>=cooldown){needNeutral=false;reset()}}else neutral=-1
            return null
        }
        if(armed>=0&&now-armed>2500){reset();needNeutral=true;cooldown=now;status="超时 · 请移开手掌";return null}
        if(armed<0){
            if(f.pose!=Pose.OPEN){since=-1;status="张开手掌并停留";return null}
            if(since<0||hypot(f.x-ax,f.y-ay)>.055f){since=now;ax=f.x;ay=f.y}
            status="检测到手掌 · 请停留"
            if(now-since>=hold){armed=now;ax=f.x;ay=f.y;status="已就绪 · 挥手"}
            return null
        }
        if(f.pose==Pose.FIST&&screenshot){
            if(fist<0)fist=now
            status="握拳确认中"
            if(now-fist>=650)return finish(Action.SHOT,now)
            return null
        }
        fist=-1
        if(f.pose!=Pose.OPEN){reset();return null}
        val dx=f.x-ax;val dy=f.y-ay
        status="已就绪 · 挥手"
        if(now-armed<100)return null
        val a=when{
            abs(dx)>=distance&&abs(dx)>=abs(dy)*1.5f->if(dx>0)Action.RIGHT else Action.LEFT
            abs(dy)>=distance&&abs(dy)>=abs(dx)*1.5f->if(dy>0)Action.DOWN else Action.UP
            else->null
        }
        return a?.let{finish(it,now)}
    }
}
