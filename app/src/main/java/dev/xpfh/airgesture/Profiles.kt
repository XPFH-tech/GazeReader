package dev.xpfh.airgesture
import android.content.Context
object Profiles {
 private fun key(rot:Int,a:Action)="r"+rot+"_"+a.name
 fun load(c:Context,rot:Int):Map<Action,Profile>{
  val p=c.getSharedPreferences("calibration_a2",0)
  return Action.entries.mapNotNull{a->
   val k=key(rot,a)
   if(!p.contains(k+"_t"))null else{
    val t=p.getFloat(k+"_t",0f);val sign=p.getInt(k+"_s",0);val e=p.getFloat(k+"_e",0f);val d=p.getLong(k+"_d",0)
    val valid=t.isFinite()&&e.isFinite()&&when(a){Action.UP,Action.DOWN->t in .045f.. .32f;Action.PALM_BACK,Action.BACK_PALM->t in .30f.. .70f&&sign in listOf(-1,1);Action.SHOT->t in 3f..4f}
    if(valid)a to Profile(t,sign,e,d) else null
   }
  }.toMap()
 }
 fun save(c:Context,rot:Int,a:Action,v:Profile):Boolean{
  val p=c.getSharedPreferences("calibration_a2",0);val k=key(rot,a)
  val other=when(a){Action.PALM_BACK->Action.BACK_PALM;Action.BACK_PALM->Action.PALM_BACK;else->null}
  if(other!=null&&load(c,rot)[other]?.faceSign==v.faceSign)return false
  return p.edit().putFloat(k+"_t",v.threshold).putInt(k+"_s",v.faceSign).putFloat(k+"_e",v.endpoint).putLong(k+"_d",v.duration).commit()
 }
 fun clear(c:Context,rot:Int){val e=c.getSharedPreferences("calibration_a2",0).edit();for(a in Action.entries)for(s in listOf("_t","_s","_e","_d"))e.remove(key(rot,a)+s);e.apply()}
}
