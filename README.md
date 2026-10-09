# 远程控制器

配套 Kindle 接收端为独立的 remote Wi-Fi KUAL 插件。

蓝牙翻页器／游戏手柄 → Android 手机 → Wi-Fi → Kindle。

支持上一页、下一页、亮度增减和息屏。手机只填写 Kindle IP，使用统一的 8080 端口，无需选择原系统或 KOReader。原生 Java，无第三方 Android 运行依赖；无需账号，仅申请网络权限。

当前版本：0.3.2。图标采用适合墨水屏的白底黑线遥控器与 Wi-Fi 符号，支持自适应图标和 Android 13 主题图标。已验证 Android 14、Smart 1 M 档及 KPW3 的 KOReader 2025.10／原系统。其他 Kindle 机型未验证；分体手柄及阅读器间自动切换仍需实测。手柄的标准按键与轴已支持，朱雀分体手柄的 2.4G 与双蓝牙连接尚待实机适配。暂不支持 3DS、iOS、手机锁屏或后台接收、远程唤醒。

## Android 安装与使用

1. 按下文构建 APK，安装 `dist/RemoteController-0.3.2-debug.apk`（Android 8.0+）。
2. 在系统设置配对翻页器／手柄。手机和 Kindle 连接同一 Wi-Fi。
3. 启动 Kindle 端接收服务，填写 Kindle IP，点击“测试连接并保存”。
4. 开启“接收遥控按键”，保持 App 前台。可用“暗屏阅读模式”降低亮度；它不锁屏。
5. 点击某个学习操作，再按实体键或推动摇杆；长按操作列表按钮可解除该操作绑定。

### Smart M 档音乐遥控

默认开启音乐模式：上一曲／下一曲翻页，音量＋／−调亮度，播放／暂停点按直接息屏，无需手机确认。音乐按键通过 Activity 和 MediaSession 接收；关闭输入或切出 App 后停止接管。其他正在播放的音乐 App 可能影响系统路由。

音乐模式的亮度脉冲不作时间节流。网络队列最多保留一个等待请求，繁忙时丢弃多余输入；超时不自动重发，避免延迟连翻。普通按键／摇杆长按调亮度约每 300ms 一步。

关闭音乐模式后，音量＋／−默认改为下一页／上一页。其他默认绑定：右／A／R1／空格下一页，左／B／L1 上一页，上／下调亮度，Start 长按 1 秒息屏。屏幕息屏按钮点按直接执行。

## KOReader 接收服务

KOReader → 工具 → 更多工具 → **KOReader远程控制**（HTTP Inspector）→ 启动 HTTP 服务，端口 8080。建议开启自动启动。打开一本书后测试。

## Kindle 原系统接收端

独立安装 [remote Wi-Fi 插件](https://github.com/sep1107/remote-wifi)，安装与端口交接方法见该仓库。

## 构建与测试

需要 JDK 17、Android SDK platform 33 和 build-tools 33.0.2，不使用 Gradle。

```sh
export JAVA_HOME="你的 JDK 17 路径"
export ANDROID_SDK_ROOT="你的 Android SDK 路径"
bash tests/run.sh
bash build.sh
```

APK 使用本地 debug 签名。默认密钥放在系统临时目录；用 `KINDLE_DEBUG_KEYSTORE` 指定固定位置以保持覆盖安装能力，不要提交密钥。更换签名后需卸载旧 APK，绑定会丢失。

测试覆盖输入防重复、亮度重复间隔、普通绑定的休眠长按、设备独立状态、IP／端口校验、HTTP 指令和错误响应拒绝；Kindle 接收端测试在插件仓库中维护。

## 协议

两个 Kindle 后端使用同一套 HTTP GET：

| 操作 | 路径 |
|---|---|
| 检测 | `/koreader/event/` |
| 上一页 | `/koreader/event/GotoViewRel/-1` |
| 下一页 | `/koreader/event/GotoViewRel/1` |
| 亮度＋ | `/koreader/event/IncreaseFlIntensity/1` |
| 亮度− | `/koreader/event/DecreaseFlIntensity/1` |
| 息屏 | `/koreader/event/RequestSuspend` |

确认响应仅表示指令已交给后端，实际效果需在 Kindle 核验。

## 许可与参考

本项目源码使用 [MIT License](LICENSE)。不附带 KOReader、LuaJIT 或 LuaSocket 二进制，其许可证由上游维护。

原系统触摸事件协议与控制思路参考 [KindleLazy](https://github.com/llakssz/KindleLazy)。KOReader HTTP 和电源接口参考 [KOReader](https://github.com/koreader/koreader)。感谢上游项目；此项目不由其作者维护。
