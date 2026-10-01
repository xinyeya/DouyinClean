# 第三方依赖

- `app/libs/xposed-api-82.jar`：官方 Xposed API 编译依赖，来自 https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar 。SHA-256：`F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25`。源项目：[rovo89/XposedBridge](https://github.com/rovo89/XposedBridge)，原项目声明见 [NOTICE.txt](https://github.com/rovo89/XposedBridge/blob/art/NOTICE.txt)，已附在 `app/libs/NOTICE-Xposed.txt`。这个 JAR 只用于编译，不包含在最终 APK 中。
- Gradle wrapper：Gradle 项目提供的标准构建启动脚本与 wrapper JAR。Gradle 采用 [Apache License 2.0](https://github.com/gradle/gradle/blob/master/LICENSE)。
- 编译工具 Android SDK、JDK 不包含在交付包中。

第三方目标应用的 APK 及反编译源码不包含在交付包中。
