package dev.xpfh.airgesture
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.widget.*

class MainActivity:Activity(){
    private val h=Handler(Looper.getMainLooper())
    private val prefs by lazy{getSharedPreferences("a1",0)}
    private lateinit var status:TextView;private lateinit var chosen:TextView
    private lateinit var shot:CheckBox;private lateinit var speed:Spinner;private lateinit var sensitivity:Spinner
    private var selected=""
    private data class Entry(val pkg:String,val title:String,val component:ComponentName)
    private fun apps():List<Entry>{
        val pm=packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).mapNotNull{r->
            val a=r.activityInfo?:return@mapNotNull null;val info=a.applicationInfo?:return@mapNotNull null
            val p=a.packageName;val lower=p.lowercase(java.util.Locale.ROOT);val label=info.loadLabel(pm).toString()
            if(!a.enabled||!a.exported||!info.enabled||p==packageName||lower.startsWith("com.android.")||lower.startsWith("dev.air2023.")||lower.startsWith("dev.xpfh.")||lower in listOf("com.tencent.mm","com.tencent.mobileqq")||listOf("magisk","rootkit","zygisk","vector","lsposed","wallet","payment","bank","alipay","settings","launcher","systemui","permissioncontroller","packageinstaller").any{lower.contains(it)})return@mapNotNull null
            val system=info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0
            if(system&&!listOf("阅读","书","浏览器","reader","browser").any{label.contains(it,true)})return@mapNotNull null
            Entry(p,label,ComponentName(p,a.name))
        }.distinctBy{it.pkg}.sortedBy{it.title.lowercase(java.util.Locale.ROOT)}
    }
    private val pulse=object:Runnable{override fun run(){
        val f=Session.frame
        status.text=Session.status+"\n无障碍："+(if(HandAccess.current!=null)"已连接" else "未连接")+" | 相机："+(if(Session.running)"运行" else "停止")+"\n手掌："+(f?.pose?.name?:"NONE")+" | 帧数："+Session.frames+"\n识别："+Session.detected+" | 实际手势回调/截屏请求："+Session.count+"\n最近动作："+Session.last
        h.postDelayed(this,200)
    }}
    override fun onCreate(b:Bundle?){super.onCreate(b);selected=prefs.getString("pkg","")?:"";home();h.post(pulse)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun home(){
        val p=LinearLayout(this).apply{orientation=1;setPadding(28,28,28,28)}
        fun text(s:String,n:Float=15f)=TextView(this).apply{text=s;textSize=n;p.addView(this)}
        fun button(s:String,action:()->Unit){p.addView(Button(this).apply{text=s;setOnClickListener{action()}})}
        text("AirGesture 隔空手势 A1",25f)
        text("本地手部识别 · 无需 Root。停止 GazeReader 相机后再使用。先张掌停留，看到已就绪后挥动。动作后移开手掌，等待冷却。")
        status=text(Session.status,17f)
        button("1. 启用隔空手势无障碍"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        button("2. 启动前置相机"){
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),10)
            else startForegroundService(Intent(this,TrackingService::class.java))
        }
        text("手掌驻留时间")
        speed=Spinner(this);speed.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("450 ms（快）","650 ms（推荐）","900 ms（稳）"));speed.setSelection(prefs.getInt("hold",1).coerceIn(0,2));p.addView(speed)
        text("挥手距离阈值")
        sensitivity=Spinner(this);sensitivity.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("灵敏 14%","标准 20%","防误触 28%"));sensitivity.setSelection(prefs.getInt("dist",1).coerceIn(0,2));p.addView(sensitivity)
        shot=CheckBox(this).apply{text="允许张掌就绪后握拳截屏（默认关闭）";isChecked=false};p.addView(shot)
        button("3. 识别测试（不会执行手势或截屏）"){
            if(!Session.running){toast("请先启动相机");return@button}
            configure();Session.pause();Session.enabled=true;Session.after=SystemClock.elapsedRealtime()+500
        }
        chosen=text("",17f);refresh()
        button("4. 选择允许控制的应用"){pick()}
        button("5. 启用隔空控制并打开所选应用"){
            val a=apps().firstOrNull{it.pkg==selected}
            if(!Session.running||HandAccess.current==null||a==null){toast("请先启动相机、启用无障碍并选择应用");return@button}
            configure();Session.pause();Session.target=a.pkg;Session.after=SystemClock.elapsedRealtime()+3500;Session.enabled=true
            try{startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}catch(e:Exception){Session.pause();toast("应用打开失败")}
        }
        button("暂停手势（相机仍运行）"){Session.pause();HandAccess.current?.hide()}
        button("停止相机与手势"){Session.pause();stopService(Intent(this,TrackingService::class.java));Session.status="已停止"}
        button("复制诊断信息"){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AirGesture A1",status.text.toString()+"\n"+Session.report()));toast("已复制")}
        text("请将平板固定，手距相机约30–60cm开始测试，手掌完整可见。截图调用系统功能，可能被系统或应用拒绝；请勿用于敏感页面。锁屏、旋转、过热、15分钟或相机异常自动停止。小窗、分屏、键盘及非目标前台不发送手势。")
        setContentView(ScrollView(this).apply{addView(p)})
    }
    private fun configure(){Session.hold=longArrayOf(450,650,900)[speed.selectedItemPosition.coerceIn(0,2)];Session.distance=floatArrayOf(.14f,.20f,.28f)[sensitivity.selectedItemPosition.coerceIn(0,2)];Session.screenshot=shot.isChecked;prefs.edit().putInt("hold",speed.selectedItemPosition).putInt("dist",sensitivity.selectedItemPosition).apply()}
    private fun refresh(){chosen.text="控制应用："+(apps().firstOrNull{it.pkg==selected}?.let{it.title+"\n"+it.pkg}?:"未选择")}
    private fun pick(){val list=apps();if(list.isEmpty()){toast("没有符合条件的应用");return}
        AlertDialog.Builder(this).setTitle("选择允许控制的应用").setItems(list.map{it.title+" · "+it.pkg}.toTypedArray()){_,i->Session.pause();selected=list[i].pkg;prefs.edit().putString("pkg",selected).apply();refresh()}.setNegativeButton("取消",null).show()}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)startForegroundService(Intent(this,TrackingService::class.java))}
    override fun onPause(){if(Session.enabled&&Session.target.isEmpty())Session.pause();super.onPause()}
    override fun onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy()}
}
