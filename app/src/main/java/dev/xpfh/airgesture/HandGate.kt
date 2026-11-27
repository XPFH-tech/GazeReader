package dev.xpfh.airgesture
import kotlin.math.abs
import kotlin.math.hypot
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

enum class Pose { NONE, OPEN, FIST, OTHER }
enum class Action(val label:String,val output:String){
 UP("向上挥手","上滑"),DOWN("向下挥手","下滑"),PALM_BACK("手心翻向手背","左滑"),BACK_PALM("手背翻向手心","右滑"),SHOT("握拳截屏","截屏")
}
data class HandFrame(val at:Long,val pose:Pose,val x:Float=0f,val y:Float=0f,val face:Float=0f,val straight:Int=0,val curled:Int=0,val size:Float=0f){
 val visible get()=pose!=Pose.NONE&&size>.045f
 val open get()=visible&&straight>=3&&curled<=1
}
object HandFeatures{
 fun extract(l:List<NormalizedLandmark>,w:Int,h:Int,at:Long):HandFrame{
  if(l.size!=21||w<=0||h<=0||l.any{!it.x().isFinite()||!it.y().isFinite()||!it.z().isFinite()||it.x() !in -.05f..1.05f||it.y() !in -.05f..1.05f})return HandFrame(at,Pose.NONE)
  val scale=minOf(w,h).toFloat()
  fun x(i:Int)=l[i].x()*w/scale
  fun y(i:Int)=l[i].y()*h/scale
  fun z(i:Int)=l[i].z()*w/scale
  fun d(a:Int,b:Int):Float{val dx=x(a)-x(b);val dy=y(a)-y(b);val dz=z(a)-z(b);return kotlin.math.sqrt(dx*dx+dy*dy+dz*dz)}
  val length=d(0,9)
  if(length !in .045f..0.85f)return HandFrame(at,Pose.NONE)
  var straight=0;var curled=0
  for(m in intArrayOf(5,9,13,17)){
   val pip=m+1;val tip=m+3;val a=d(m,pip);val b=d(pip,tip);val c=d(m,tip)
   if(a<.004f||b<.004f)continue
   val cos=(a*a+b*b-c*c)/(2*a*b)
   if(cos<-.56f&&d(0,tip)>d(0,pip)+length*.10f)straight++
   if(cos>-.42f&&d(0,tip)<d(0,pip)+length*.17f)curled++
  }
  val ax=x(5)-x(0);val ay=y(5)-y(0);val az=z(5)-z(0)
  val bx=x(17)-x(0);val by=y(17)-y(0);val bz=z(17)-z(0)
  val nx=ay*bz-az*by;val ny=az*bx-ax*bz;val nz=ax*by-ay*bx
  val norm=kotlin.math.sqrt(nx*nx+ny*ny+nz*nz)
  val face=if(norm>.0001f)(nz/norm).coerceIn(-1f,1f) else 0f
  val center=intArrayOf(0,5,9,13,17)
  return HandFrame(at,when{straight>=3&&curled<=1->Pose.OPEN;curled>=3&&straight<=1->Pose.FIST;else->Pose.OTHER},w/scale-center.map{x(it)}.average().toFloat(),center.map{y(it)}.average().toFloat(),face,straight,curled,length)
 }
}
data class Profile(val threshold:Float,val faceSign:Int=0,val endpoint:Float=0f,val duration:Long=0)
data class CalResult(val profile:Profile?,val message:String)
class Calibration(val action:Action,val begin:Long){
 var status="请准备";private set
 var result:CalResult?=null;private set
 private var last=-1L;private var stable=-1L;private var start:HandFrame?=null
 private var endSince=-1L;private var endCount=0;private var motionAt=0L
 private fun finish(p:Profile?,msg:String){result=CalResult(p,msg);status=msg}
 fun sample(f:HandFrame?,now:Long){
  if(result!=null)return
  if(now-begin>14000){finish(null,"校准超时 · 请重试");return}
  if(now<begin+1500){status="准备 "+((begin+1500-now+999)/1000)+" 秒 · 张开手掌";return}
  if(f==null||f.at<=last||now-f.at !in 0..500)return
  val gap=last>=0&&f.at-last>500;last=f.at
  if(gap){if(motionAt!=0L){finish(null,"画面中断 · 请重试");return};stable=-1;start=null}
  if(motionAt==0L){
   val old=start
   if(!f.open||abs(f.face)<.48f){stable=-1;start=null;status="起始姿势：张掌，"+when(action){Action.PALM_BACK->"手心朝相机";Action.BACK_PALM->"手背朝相机";else->"手掌朝相机"};return}
   if(old==null||hypot(f.x-old.x,f.y-old.y)>.055f||old.face*f.face<0){stable=now;start=f}
   if(now-stable<500){status="起始姿势确认 · "+((now-stable)*100/500).coerceIn(0,100)+"%";return}
   start=f;motionAt=now;status="现在："+action.label+"，到终点保持片刻";return
  }
  val a=start?:return
  if(now-motionAt>4500){finish(null,"动作未达到校准条件 · 请重试");return}
  if(!f.visible){endSince=-1;endCount=0;return}
  val dx=f.x-a.x;val dy=f.y-a.y
  val scaleRatio=f.size/a.size
  val sign=if(a.face>=0)1 else -1
  val valid=scaleRatio in .45f..2.0f&&when(action){
   Action.UP->f.open&&dy<-.065f&&abs(dy)>abs(dx)*.8f&&f.face*sign>.18f
   Action.DOWN->f.open&&dy>.065f&&abs(dy)>abs(dx)*.8f&&f.face*sign>.18f
   Action.PALM_BACK,Action.BACK_PALM->f.open&&f.face*sign<-.45f&&hypot(dx,dy)<maxOf(.22f,a.size*2.1f)
   Action.SHOT->f.curled>=3&&f.straight<=1&&hypot(dx,dy)<maxOf(.18f,a.size*1.8f)
  }
  if(!valid){endSince=-1;endCount=0;return}
  if(endSince<0)endSince=now
  endCount++;status="终点确认 · 保持姿势"
  if(now-endSince<220||endCount<3)return
  val p=when(action){
   Action.UP,Action.DOWN->Profile((abs(dy)*.58f).coerceIn(.045f,.32f),0,abs(dx)/(abs(dy).coerceAtLeast(.01f)),now-motionAt)
   Action.PALM_BACK,Action.BACK_PALM->Profile((minOf(abs(a.face),abs(f.face))*.65f).coerceIn(.30f,.70f),sign,abs(f.face),now-motionAt)
   Action.SHOT->Profile(f.curled.toFloat().coerceIn(3f,4f),0,0f,now-motionAt)
  }
  finish(p,action.label+" 校准成功")
 }
}
class HandGate(private val profiles:Map<Action,Profile>,private val screenshot:Boolean=false,private val hold:Long=330L){
 var status="请张开单手并停稳";private set
 private var last=-1L;private var since=-1L;private var armed=-1L
 private var neutral=-1L;private var cooldown=0L;private var needNeutral=false
 private var anchor:HandFrame?=null;private var candidate:Action?=null;private var candidateAt=0L;private var hits=0
 private var smoothX=0f;private var smoothY=0f;private var lastFace=0f;private var crossed=false;private var verticalLock=false
 fun reset(){last=-1;since=-1;armed=-1;neutral=-1;anchor=null;candidate=null;hits=0;crossed=false;verticalLock=false;status="请张开单手并停稳"}
 private fun finish(a:Action,now:Long):Action{reset();needNeutral=true;cooldown=now+1100;status="已识别 "+a.label;return a}
 fun sample(f:HandFrame,now:Long):Action?{
  if(f.at<=last)return null
  if(now-f.at !in 0..500||!f.x.isFinite()||!f.y.isFinite()||!f.face.isFinite()){reset();return null}
  if(last>=0&&f.at-last>500)reset()
  last=f.at
  if(needNeutral){status="请移开手掌 · 等待冷却";if(!f.visible){if(neutral<0)neutral=now;if(now-neutral>=300&&now>=cooldown){needNeutral=false;reset()}}else neutral=-1;return null}
  if(armed<0){
   if(!f.open||abs(f.face)<.30f){since=-1;anchor=null;status="等待张掌 · 单手完整朝向相机";return null}
   val a=anchor
   if(a==null||hypot(f.x-a.x,f.y-a.y)>.065f||a.face*f.face<0){since=now;anchor=f}
   status="已识别手掌 · 保持不动 "+((now-since)*100/hold).coerceIn(0,100)+"%"
   if(now-since>=hold){anchor=f;armed=now;smoothX=f.x;smoothY=f.y;lastFace=f.face;status="已就绪 · 上下挥手 / 翻掌"}
   return null
  }
  val a=anchor?:run{reset();return null}
  if(now-armed>3200){reset();needNeutral=true;cooldown=now;status="超时 · 请移开手掌";return null}
  if(!f.visible){candidate=null;hits=0;status="手掌暂时丢失 · 请保持在镜头内";return null}
  if(f.size/a.size !in .45f..2.0f){reset();return null}
  smoothX=.35f*smoothX+.65f*f.x;smoothY=.35f*smoothY+.65f*f.y
  val dx=smoothX-a.x;val dy=smoothY-a.y;val sign=if(a.face>=0)1 else -1
  if(abs(f.face)<.30f||lastFace*f.face<0)crossed=true
  lastFace=f.face
  val vertical=if(dy<0)Action.UP else Action.DOWN
  val vp=profiles[vertical]
  if(vp!=null&&abs(dy)>vp.threshold*.70f&&abs(dy)>abs(dx)*1.05f&&!crossed&&f.face*sign>.20f)verticalLock=true
  val flip=profiles.entries.firstOrNull{(act,p)->act in listOf(Action.PALM_BACK,Action.BACK_PALM)&&p.faceSign==sign&&abs(a.face)>=p.threshold&&f.face*sign<=-p.threshold}
  val shot=profiles[Action.SHOT]
  val next=when{
   screenshot&&shot!=null&&f.curled>=shot.threshold.toInt()&&f.straight<=1&&hypot(dx,dy)<maxOf(.15f,a.size*1.7f)->Action.SHOT
   f.open&&!verticalLock&&crossed&&flip!=null&&hypot(dx,dy)<maxOf(.20f,a.size*1.9f)->flip.key
   f.open&&!crossed&&vp!=null&&f.face*sign>.20f&&abs(dy)>=vp.threshold&&abs(dy)>abs(dx)*maxOf(1.05f,1.25f-vp.endpoint*.20f)->vertical
   else->null
  }
  if(next==null){candidate=null;hits=0;status=when{crossed->"翻掌中 · 请保持终点朝向";vp!=null&&abs(dy)>vp.threshold*.25f->"上下挥动 · "+(abs(dy)*100/vp.threshold).toInt().coerceIn(0,100)+"%";else->"已就绪 · 上下挥手 / 翻掌"};return null}
  if(candidate!=next){candidate=next;candidateAt=now;hits=1}else hits++
  val duration=when(next){Action.SHOT->500L;Action.PALM_BACK,Action.BACK_PALM->160L;else->75L}
  status=next.label+" · 确认 "+((now-candidateAt)*100/duration).coerceIn(0,100)+"%"
  return if(hits>=2&&now-candidateAt>=duration)finish(next,now)else null
 }
}
