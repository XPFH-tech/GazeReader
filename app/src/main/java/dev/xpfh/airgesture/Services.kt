package dev.xpfh.airgesture
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import android.util.Log
import androidx.lifecycle.LifecycleService

object Session {
    @Volatile var running=false;@Volatile var enabled=false;@Volatile var target=""
    @Volatile var frame:HandFrame?=null;@Volatile var status="未启动相机"
    @Volatile var hold=650L;@Volatile var distance=.20f;@Volatile var screenshot=false
    @Volatile var after=0L;@Volatile var epoch=0L
    @Volatile var count=0;@Volatile var detected=0;@Volatile var frames=0
    @Volatile var last="尚未触发";@Volatile var feedbackUntil=0L
    @Volatile var overlay=true;@Volatile var overlayTop=false;@Volatile var hideUntil=0L
    @Volatile var diagnostic="未选择目标"
    fun poseLabel(p:Pose?)=when(p){Pose.OPEN->"张开手掌";Pose.FIST->"握拳";Pose.OTHER->"其他手势";else->"未检测到有效单手"}
    private val history=java.util.ArrayDeque<String>()
    @Synchronized fun note(s:String){if(history.peekLast()?.endsWith(" "+s)==true)return;if(history.size>=80)history.removeFirst();history.addLast(SystemClock.elapsedRealtime().toString()+" "+s);Log.i("AirGestureA1",s)}
    @Synchronized fun report()=history.joinToString("\n")
    fun feedback(s:String){last=s;feedbackUntil=SystemClock.elapsedRealtime()+2200;note(s)}
    fun pause(){enabled=false;target="";epoch++;feedbackUntil=0;hideUntil=0;after=0}
}
fun rotation(c:Context)=(c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
class HandAccess:AccessibilityService(){
    companion object {var current:HandAccess?=null;private set}
    private var hint:TextView?=null;private var lastHint="";private var busy=false;private var hintTop=false
    var targetActive=false;private set
    private fun rejected(s:String):Rect?{Session.diagnostic=s;return null}
    override fun onServiceConnected(){super.onServiceConnected();current=this;Session.note("ACCESS_CONNECTED")}
    override fun onAccessibilityEvent(e:AccessibilityEvent?){}
    override fun onInterrupt(){Session.pause();hide()}
    override fun onDestroy(){if(current===this)current=null;Session.pause();hide();super.onDestroy()}
    fun bounds(pkg:String):Rect?{
        targetActive=false
        if(pkg.isBlank())return rejected("未指定应用")
        if(getSystemService(KeyguardManager::class.java).isKeyguardLocked)return rejected("锁屏")
        val root=rootInActiveWindow?:return rejected("无活动窗口")
        val id:Int;val actual:String
        try{id=root.windowId;actual=root.packageName?.toString()?:""}finally{root.recycle()}
        if(actual!=pkg)return rejected("当前不是选定应用")
        targetActive=true
        val screen=(getSystemService(WINDOW_SERVICE) as WindowManager).maximumWindowMetrics.bounds
        val ws=windows
        try{
            if(ws.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD})return rejected("输入法可见")
            val active=ws.firstOrNull{it.id==id&&it.type==AccessibilityWindowInfo.TYPE_APPLICATION}?:return rejected("未找到应用窗口")
            val b=Rect();active.getBoundsInScreen(b)
            if(!b.intersect(screen)||b.width()<screen.width()*.90f||b.height()<screen.height()*.82f)return rejected("非全屏窗口")
            for(w in ws){if(w.id==id||w.type!=AccessibilityWindowInfo.TYPE_APPLICATION)continue
                val other=Rect();w.getBoundsInScreen(other)
                if(w.isActive||w.isFocused||other.intersect(screen)&&other.width()*other.height()>screen.width()*screen.height()*.20f&&w.layer>active.layer)return rejected("其他窗口覆盖")}
            Session.diagnostic="目标全屏窗口已就绪"
            return b
        }finally{ws.forEach{it.recycle()}}
    }
    fun show(s:String){
        if(!Session.overlay||SystemClock.elapsedRealtime()<Session.hideUntil){hide();return}
        if(hint!=null&&hintTop!=Session.overlayTop)hide()
        if(s==lastHint&&hint!=null)return
        try{
            if(hint==null){
                val v=TextView(this).apply{setTextColor(Color.WHITE);textSize=16f;gravity=Gravity.CENTER;maxLines=2;val d=resources.displayMetrics.density;setPadding((18*d).toInt(),(9*d).toInt(),(18*d).toInt(),(9*d).toInt());background=GradientDrawable().apply{cornerRadius=20*d;setColor(0xe6222529.toInt())}}
                val lp=WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT)
                hintTop=Session.overlayTop;lp.gravity=(if(hintTop)Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL;lp.y=(42*resources.displayMetrics.density).toInt()
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v,lp);hint=v
            }
            hint?.text=s;hint?.setTextColor(when{ s.contains("已完成")||s.contains("已接受")->Color.rgb(166,244,188);s.contains("失败")||s.contains("拒绝")||s.contains("暂停")||s.contains("取消")||s.contains("超时")||s.contains("丢失")->Color.rgb(255,187,150);s.contains("已就绪")||s.contains("已识别")||s.contains("确认中")||s.contains("移动")->Color.rgb(255,233,155);else->Color.WHITE});lastHint=s
        }catch(e:Exception){Session.note("OVERLAY "+e.javaClass.simpleName)}
    }
    fun hide(){hint?.let{try{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)}catch(_:Exception){}};hint=null;lastHint=""}
    fun send(pkg:String,a:Action){
        if(busy||!Session.enabled||Session.target!=pkg){Session.feedback("未发送：已暂停或忙碌");return}
        val b=bounds(pkg)?:run{Session.feedback("未发送：窗口已变化");return}
        if(a==Action.SHOT){
            if(!Session.screenshot)return
            Session.hideUntil=SystemClock.elapsedRealtime()+1000;hide()
            val ok=try{performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)}catch(_:Exception){false}
            if(ok){Session.count++;Session.feedback("系统已接受截屏请求")}else Session.feedback("系统拒绝截屏")
            return
        }
        fun x(f:Float)=b.left+b.width()*f
        fun y(f:Float)=b.top+b.height()*f
        val p=Path()
        when(a){
            Action.LEFT->{p.moveTo(x(.72f),y(.50f));p.lineTo(x(.30f),y(.50f))}
            Action.RIGHT->{p.moveTo(x(.28f),y(.50f));p.lineTo(x(.70f),y(.50f))}
            Action.UP->{p.moveTo(x(.50f),y(.73f));p.lineTo(x(.50f),y(.31f))}
            Action.DOWN->{p.moveTo(x(.50f),y(.30f));p.lineTo(x(.50f),y(.72f))}
            else->return
        }
        busy=true;val generation=Session.epoch
        Session.feedback("正在发送 "+a.label)
        val ok=try{dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,320)).build(),object:GestureResultCallback(){
            override fun onCompleted(g:GestureDescription?){busy=false;if(Session.epoch==generation&&Session.running){Session.count++;Session.feedback("手势已完成 · "+a.label+" · "+Session.count+" 次")}}
            override fun onCancelled(g:GestureDescription?){busy=false;if(Session.epoch==generation&&Session.running)Session.feedback("手势已取消")}
        },null)}catch(_:Exception){false}
        if(!ok){busy=false;Session.feedback("系统拒绝 "+a.label)}
    }
}
class TrackingService:LifecycleService(){
    private val h=Handler(Looper.getMainLooper());private var pipe:CameraPipe?=null
    private var rot=0;private var started=0L;private var epoch=-1L;private var gate:HandGate?=null
    private var blocked=true
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){stopNow("锁屏 · 已停止")}}
    private val tick=object:Runnable{override fun run(){
        if(!Session.running)return
        val now=SystemClock.elapsedRealtime()
        if(rotation(this@TrackingService)!=rot){stopNow("方向变化 · 请重新启动");return}
        if(now-started>15*60*1000L){stopNow("15分钟自动停止");return}
        if(getSystemService(PowerManager::class.java).currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE){stopNow("温度过高 · 已停止");return}
        val f=Session.frame;val access=HandAccess.current
        if((f==null&&now-started>15000)||(f!=null&&now-f.at>5000)){stopNow("相机无新画面 · 已停止");return}
        if(epoch!=Session.epoch){epoch=Session.epoch;gate=null;blocked=true}
        val test=Session.enabled&&Session.target.isEmpty()
        val targetOk=Session.enabled&&!test&&access?.bounds(Session.target)!=null
        val allowed=Session.enabled&&now>=Session.after&&(test||targetOk)
        if(!allowed||f==null||now-f.at>500){
            if(!blocked){gate=null;blocked=true}
            Session.status=when{!Session.enabled->if(f==null)"正在启动前置相机" else "相机已就绪 · 请启用识别测试或控制";now<Session.after->"即将启用 · 请准备";access==null&&!test->"暂停 · 无障碍未连接";!allowed->"暂停 · "+Session.diagnostic;else->"等待相机新画面"}
            if(Session.enabled&&!test&&access?.targetActive==true)access.show(Session.status) else access?.hide()
        }else{
            if(blocked||gate==null){gate=HandGate(Session.hold,Session.distance,Session.screenshot);blocked=false}
            val g=gate!!
            val action=g.sample(f,now)
            if(action!=null){Session.detected++;if(test)Session.feedback("识别测试："+action.label+"（未执行）") else access?.send(Session.target,action)}
            Session.status=g.status
            if(!test)access?.show(if(now<Session.feedbackUntil)Session.last+"\n请移开手掌，等待下一次识别" else Session.status+(if(g.status.startsWith("已就绪")&&Session.screenshot)"\n也可握拳截屏" else "")) else access?.hide()
        }
        h.postDelayed(this,100)
    }}
    override fun onCreate(){super.onCreate();registerReceiver(receiver,IntentFilter(Intent.ACTION_SCREEN_OFF))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        super.onStartCommand(intent,flags,startId)
        if(intent?.action=="stop"){stopNow("已手动停止");return START_NOT_STICKY}
        if(Session.running)return START_NOT_STICKY
        try{
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("hand","隔空手势",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,1,Intent(this,TrackingService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
            val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            startForeground(3321,Notification.Builder(this,"hand").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("AirGesture · 前置相机运行").setContentText("点击返回控制页，或停止相机").setContentIntent(open).addAction(0,"停止",stop).setOngoing(true).build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            rot=rotation(this);started=SystemClock.elapsedRealtime();Session.pause();Session.frame=null;Session.frames=0;Session.count=0;Session.detected=0;Session.last="尚未触发";Session.running=true;Session.status="正在启动相机"
            pipe=CameraPipe(this,this,rot,{Session.frame=it;Session.frames++},{msg->h.post{stopNow(msg)}}).also{it.start()}
            h.post(tick)
        }catch(e:Throwable){stopNow("启动失败："+e.javaClass.simpleName)}
        return START_NOT_STICKY
    }
    private fun stopNow(s:String){Session.note(s);Session.pause();Session.running=false;Session.frame=null;Session.status=s;HandAccess.current?.hide();h.removeCallbacks(tick);pipe?.stop();pipe=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){Session.pause();Session.running=false;Session.frame=null;HandAccess.current?.hide();h.removeCallbacksAndMessages(null);pipe?.stop();pipe=null;try{unregisterReceiver(receiver)}catch(_:Exception){};super.onDestroy()}
}
