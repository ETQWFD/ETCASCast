# ETCAS投屏

轻松投屏 · 极速流畅

ETCAS Cast 是一款开源的本地投屏工具，支持将手机中的视频、图片、文件一键投屏到电视 / 投影 / 盒子等局域网设备，也支持屏幕镜像与链接投屏。

## 演示

<video src="demo/ETCASCast-demo.mp4" controls width="360"></video>

## 功能

- 本地投屏：选择手机中的视频 / 图片 / 文件（支持所有格式），自动搜索设备并投屏
- 同步模式：实时镜像手机屏幕到局域网设备，电视或电脑浏览器打开地址即可查看
- 链接投屏：输入链接自动获取视频信息（标题 / 封面 / 类型），一键投屏
- 扫码投屏：扫描哔哩哔哩 / 酷喵 / 芒果等投屏二维码或视频链接
- 设备搜索：支持 DLNA / 哔哩哔哩 / 酷喵 / 芒果及主流电视品牌
- 播放控制：倍速（0.5x - 2.0x）、清晰度（自动 / 高清 / 标清 / 流畅）、音量控制
- 个性化：樱花粉 / 海蓝 / 橙黄 / 碧绿四种主题色，明亮 / 暗黑 / 跟随系统三种显示模式
- 多语言：中文简体 / English / 中文繁體 / 日本語
- 检查更新：从 GitHub Releases 检测新版本并下载

## 下载

前往 [GitHub Releases](https://github.com/ETQWFD/ETCASCast/releases) 下载最新 APK。

## 构建

```bash
export JAVA_HOME=<JDK17>
export ANDROID_HOME=<Android SDK>
gradle assembleRelease
```

签名配置见 `gradle.properties`（密钥库不随源码公开）。

## 许可

MIT License，详见 [LICENSE](LICENSE)。
