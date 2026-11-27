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
    private lateinit var status:TextView;private lateinit var chosen:TextView;private lateinit var live:TextView
    private lateinit var shot:CheckBox;private lateinit var speed:Spinner
    private lateinit var calibrationStatus:TextView
    private val calButtons=mutableMapOf<Action,Button>()
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
        live.text=if(SystemClock.elapsedRealtime()<Session.feedbackUntil)Session.last+"\n"+Session.status else Session.status
        live.setTextColor(if(Session.status.startsWith("已就绪"))0xff13734a.toInt() else if(Session.status.contains("暂停")||Session.status.contains("失败"))0xffa04b14.toInt() else 0xff202124.toInt())
        status.text="无障碍："+(if(HandAccess.current!=null)"已连接" else "未连接")+" | 相机："+(if(Session.running)"运行" else "停止")+"\n手掌："+Session.poseLabel(f?.pose)+" | 帧数："+Session.frames+"\n识别："+Session.detected+" | 实际手势回调/截屏请求："+Session.count+"\n最近动作："+Session.last
        val rot=rotation(this@MainActivity);val profiles=Profiles.load(this@MainActivity,rot)
        calibrationStatus.text="当前方向："+rot+" · 已校准 "+profiles.size+"/5\n"+Session.calMessage
        for((a,b) in calButtons){b.text=(if(profiles.containsKey(a))"已校准 · 重校 " else "校准 ")+a.label;b.isEnabled=Session.running&&Session.calibration==null}
        h.postDelayed(this,200)
    }}
    override fun onCreate(b:Bundle?){super.onCreate(b);selected=prefs.getString("pkg","")?:"";home();h.post(pulse)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun home(){
        val p=LinearLayout(this).apply{orientation=1;setPadding(28,28,28,28)}
        fun text(s:String,n:Float=15f)=TextView(this).apply{text=s;textSize=n;p.addView(this)}
        fun button(s:String,action:()->Unit){p.addView(Button(this).apply{text=s;setOnClickListener{action()}})}
        text("AirGesture 隔空手势 A2",25f)
        text("本地手部识别 · 无需 Root。停止 GazeReader 相机后再使用。先完成五种手势的独立校准；同一方向使用同一只手。张掌停稳后上下挥手、翻掌或握拳，动作后移开手掌。")
        live=text(Session.status,23f);live.gravity=android.view.Gravity.CENTER;live.setPadding(16,28,16,28);live.background=android.graphics.drawable.GradientDrawable().apply{cornerRadius=18*resources.displayMetrics.density;setColor(0xffedf3f7.toInt())}
        status=text("",15f)
        button("1. 启用隔空手势无障碍"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        button("2. 启动前置相机"){
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),10)
            else startForegroundService(Intent(this,TrackingService::class.java))
        }
        text("手掌驻留时间")
        speed=Spinner(this);speed.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("250 ms（快）","330 ms（推荐）","500 ms（稳）"));speed.setSelection(prefs.getInt("holdA2",1).coerceIn(0,2));p.addView(speed)
        text("3. 独立校准（不派发任何动作）",20f)
        calibrationStatus=text("未校准",16f)
        text("每项：先按提示张掌并保持，出现“现在”再执行动作，终点保持片刻。两种翻掌务必使用同一只手。校准随屏幕方向保存。")
        for(a in Action.entries){val b=Button(this).apply{text="校准 "+a.label;setOnClickListener{beginCalibration(a)}};calButtons[a]=b;p.addView(b)}
        button("清除当前方向校准"){AlertDialog.Builder(this).setTitle("清除五项校准？").setPositiveButton("清除"){_,_->Session.pause();Profiles.clear(this,rotation(this));Session.profiles=emptyMap();Session.calMessage="已清除当前方向校准"}.setNegativeButton("取消",null).show()}
        shot=CheckBox(this).apply{text="允许张掌就绪后握拳截屏（默认关闭）";isChecked=false};p.addView(shot)
        val hints=CheckBox(this).apply{text="控制应用内显示实时文字提示";isChecked=prefs.getBoolean("overlay",true);setOnCheckedChangeListener{_,v->Session.overlay=v;prefs.edit().putBoolean("overlay",v).apply();if(!v)HandAccess.current?.hide()}};p.addView(hints);Session.overlay=hints.isChecked
        val top=CheckBox(this).apply{text="提示显示在屏幕顶部（默认底部）";isChecked=prefs.getBoolean("overlayTop",false);setOnCheckedChangeListener{_,v->Session.overlayTop=v;prefs.edit().putBoolean("overlayTop",v).apply();HandAccess.current?.hide()}};p.addView(top);Session.overlayTop=top.isChecked
        button("4. 识别测试（不会执行手势或截屏）"){
            if(!Session.running){toast("请先启动相机");return@button}
            if(!configure())return@button;Session.pause();Session.test=true;Session.enabled=true;Session.after=SystemClock.elapsedRealtime()+500
        }
        button("5. 开启全局隔空手势（无需选择应用）"){
            if(!Session.running||HandAccess.current==null){toast("请启动相机并启用无障碍");return@button}
            if(!configure())return@button
            Session.pause();Session.global=true;Session.enabled=true;Session.after=SystemClock.elapsedRealtime()+1800
            toast("全局控制已启用，返回桌面或其他应用即可使用")
        }
        chosen=text("",17f);refresh()
        button("可选：仅控制指定应用"){pick()}
        button("开启单应用模式并打开"){
            val a=apps().firstOrNull{it.pkg==selected}
            if(!Session.running||HandAccess.current==null||a==null){toast("请先启动相机、启用无障碍并选择应用");return@button}
            if(!configure())return@button;Session.pause();Session.target=a.pkg;Session.after=SystemClock.elapsedRealtime()+3500;Session.enabled=true
            try{startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}catch(e:Exception){Session.pause();toast("应用打开失败")}
        }
        button("暂停手势（相机仍运行）"){Session.pause();HandAccess.current?.hide()}
        button("停止相机与手势"){Session.pause();stopService(Intent(this,TrackingService::class.java));Session.status="已停止"}
        button("复制诊断信息"){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AirGesture A2",live.text.toString()+"\n"+status.text+"\n"+Session.diagnostic+"\n"+Session.report()));toast("已复制")}
        text("请将平板固定，手距相机约30–60cm开始测试，手掌完整可见。截图调用系统功能，可能被系统或应用拒绝；请勿用于敏感页面。锁屏、过热或相机异常自动停止。旋转会重新打开相机并读取该方向校准；未校准暂停。全局模式尝试控制当前活动应用窗口（包括桌面及分屏），系统授权、支付等敏感窗口、键盘、覆盖及过小窗口暂停。系统截屏限制不会被绕过。")
        setContentView(ScrollView(this).apply{addView(p)})
    }
    private fun beginCalibration(a:Action){
        if(!Session.running||Session.frame==null){toast("请先启动相机并等待画面");return}
        if(Session.cameraRot!=rotation(this)){toast("相机正在切换方向，请重试");return}
        Session.pause();HandAccess.current?.hide()
        Session.calMessage="正在校准："+a.label
        Session.calibration=Calibration(a,SystemClock.elapsedRealtime())
    }
    private fun configure():Boolean{
        val profiles=Profiles.load(this,rotation(this))
        if(profiles.isEmpty()){toast("请先校准至少一项手势");return false}
        Session.profiles=profiles;Session.hold=longArrayOf(250,330,500)[speed.selectedItemPosition.coerceIn(0,2)]
        Session.screenshot=shot.isChecked
        prefs.edit().putInt("holdA2",speed.selectedItemPosition).apply()
        return true
    }
    private fun refresh(){chosen.text="控制应用："+(apps().firstOrNull{it.pkg==selected}?.let{it.title+"\n"+it.pkg}?:"未选择")}
    private fun pick(){val list=apps();if(list.isEmpty()){toast("没有符合条件的应用");return}
        AlertDialog.Builder(this).setTitle("选择允许控制的应用").setItems(list.map{it.title+" · "+it.pkg}.toTypedArray()){_,i->Session.pause();selected=list[i].pkg;prefs.edit().putString("pkg",selected).apply();refresh()}.setNegativeButton("取消",null).show()}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)startForegroundService(Intent(this,TrackingService::class.java))}
    override fun onPause(){if(Session.test||Session.calibration!=null)Session.pause();super.onPause()}
    override fun onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy()}
}
