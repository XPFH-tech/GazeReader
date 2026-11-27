package dev.xpfh.airgesture
import android.content.Context
object Profiles {
 private fun key(rot:Int,a:Action)="r"+rot+"_"+a.name+(when(a){Action.UP->"_wrist_v2";Action.PALM_BACK,Action.BACK_PALM->"_a3_stationary";else->""})
 fun load(c:Context,rot:Int):Map<Action,Profile>{
  val p=c.getSharedPreferences("calibration_a2",0)
  return Action.entries.mapNotNull{a->
   val k=key(rot,a)
   if(!p.contains(k+"_t"))null else{
    val t=p.getFloat(k+"_t",0f);val sign=p.getInt(k+"_s",0);val e=p.getFloat(k+"_e",0f);val d=p.getLong(k+"_d",0)
    val valid=t.isFinite()&&e.isFinite()&&when(a){Action.UP->t in .018f.. .22f&&e in .25f.. .60f&&sign in listOf(-1,1);Action.DOWN->t in .045f.. .32f;Action.PALM_BACK,Action.BACK_PALM->t in .25f.. .65f&&e in .04f.. .085f&&sign in listOf(-1,1);Action.LEFT,Action.RIGHT->t in .085f.. .32f&&e in .25f.. .65f&&sign in listOf(-1,1);Action.SHOT->t in 3f..4f}
    if(valid)a to Profile(t,sign,e,d) else null
   }
  }.toMap()
 }
 fun save(c:Context,rot:Int,a:Action,v:Profile):Boolean{
  val p=c.getSharedPreferences("calibration_a2",0);val k=key(rot,a)
  if(a.isFlip()&&load(c,rot).any{(other,profile)->other!=a&&other.isFlip()&&(if(other.palmStart()==a.palmStart())profile.faceSign!=v.faceSign else profile.faceSign==v.faceSign)})return false
  return p.edit().putFloat(k+"_t",v.threshold).putInt(k+"_s",v.faceSign).putFloat(k+"_e",v.endpoint).putLong(k+"_d",v.duration).commit()
 }
 fun clear(c:Context,rot:Int){val e=c.getSharedPreferences("calibration_a2",0).edit();for(a in Action.entries)for(s in listOf("_t","_s","_e","_d"))e.remove(key(rot,a)+s);e.apply()}
}
