# AirGesture · 隔空手势 A1R1
独立 Android 实验应用。首次目标 MatePad Air 2023 / HarmonyOS 4.2 / Android 12，未真机验证。无需 Root、Vector、接近光传感器、软重启；与 GazeReader 不同时占用前置相机。
手掌稳定停留约650ms，出现就绪提示后2.5秒内向上下左右挥动；握拳截屏需独立开启，先张掌就绪再握拳650ms。动作后移开手掌解除冷却。识别仅在本地，手势只派发至明确选择的全屏应用；小窗、分屏、键盘、锁屏暂停或停止。可在软件内测试识别，不执行任何系统动作。
项目为独立源码树，暂以 GazeReader 的 airgesture-a1 分支作为 CI 构建载体，不改动 main 或 U2。正式仓库需另建。
参考 [GestureControl](https://github.com/Git-Frk/GestureControl) 的 Kotlin/CameraX/MediaPipe、确认状态机与无障碍控制设计，固定提交 be9d90553e0e0ec4d6f5d531594e26a6fdd9076c；参考 [Air Gesture Hub](https://github.com/Autumn-oy/Air-Gesture-Hub) 的手掌激活和冷却概念，固定提交 ab634b6e0b622d51ef3b7094c0532b656a44b3de。未复制上述项目业务代码。相机框架由本账号 GazeReader 相机处理逻辑调整。Google MediaPipe 手模型从固定官方模型 URL 下载并校验哈希，附 Apache-2.0。所有设备兼容性、手势识别精度及截屏行为待验证。

## A1R1 文字互动提示
独立识别页大字状态：等待手掌、姿势不符、驻留百分比、就绪、移动进度、握拳确认、超时、冷却；区分识别成功与系统回调。选定应用内非触摸提示支持开关和顶部/底部位置；仅在目标应用活动时显示，截屏请求时短暂隐藏提示，不能保证系统截图完全不含提示。目标窗口不符合条件时显示原因，不放宽手势派发保护。原识别阈值、相机和动作保持不变。CI 调试签名可能不同，无法保证覆盖安装。尚未真机验证。
