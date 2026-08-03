# mHook 适配版真机验证清单

APK：`G:\mHookdemo\mHook\app\build\outputs\apk\debug\app-debug.apk`

## 0. 环境准备

- [ ] 真机已 root，已安装 **LSPosed 框架**（Zygisk 或 Riru 版均可，API 82 兼容）
- [ ] `adb install -r app-debug.apk` 安装成功
- [ ] 在 LSPosed 中启用 **mHook 管理器**，作用域勾选目标测试应用
- [ ] 首次打开 mHook 管理器，授予 root 权限
  - `su.java` 会自动执行：`setenforce 0`、`mount -o remount /data`、`mkdir /data/mHook`、`chmod -R 777`
  - 将 `mk` 二进制释放到 `/data/mHook/mHookApp/lib/`

## 1. 脱壳测试（MemoryDexDumper）

- [ ] 为测试包创建 dump 目录（脱壳仅对存在 dump 目录的包生效）：
  - 在管理器界面勾选"脱壳"选项，或手工执行：
    `adb shell su -c "mkdir -p /data/mHook/<测试包名>/dump/"`
- [ ] 重启目标应用（必要时 LSPosed 重启/强制停止）
- [ ] 观察 logcat 出现脱壳日志：
  ```
  adb logcat -s XposedBridge:I | grep MemoryDexDumper
  ```
  预期：`MemoryDexDumper dump: source-<size>-<crc>.dex`
- [ ] 检查 dump 产物：
  ```
  adb shell su -c "ls -la /data/mHook/<测试包名>/dump/"
  ```
- [ ] 用 jadx / baksmali 打开 dump 出的 dex，确认可正常反编译、方法体完整

### 脱壳路径覆盖（三层方案）
- [ ] **内存 dex**：加固后经 `InMemoryDexClassLoader` 加载 → 第一层 hook 直接拷 buffer
- [ ] **磁盘 dex**：解密后写盘再 `DexClassLoader`/`DexFile` 加载 → 第二层 hook 拷贝文件
- [ ] **兜底**：直接反射 `Class.loadClass`，经 `dexCache→dexFile→mCookie` 用 Unsafe 读 ArtDexFile 内存

## 2. 热修复测试（StartFix）

- [ ] 用管理器内 MKFixActivity（`mk` 工具）生成修复包 `mk.apk`（内含 dex / resources.arsc）
- [ ] 放入 `/data/mHook/<测试包名>/fix/mk.apk`
- [ ] 重启目标应用，确认修复生效（无 `"热修复失败"` 日志）
- [ ] 推荐使用 mode 2（`HotfixHelper.applyPatch`）
- [ ] **负向测试**：删除 `fix/mk.apk` 后重启，确认不崩溃、无异常（`StartFix.java` 已加 `fixDir` 存在性检查）

## 3. 功能回归

- [ ] 自定义 HOOK（StartHook）：管理器配置 hook 规则 → 目标应用日志输出 `test---...`
- [ ] 行为分析（XpXWFX / AppXWFX）：配置后观察 API 调用/日志输出
- [ ] Web 论坛页（WebActivity）：确认改用系统 WebView 后正常加载、JS 接口可用
- [ ] 日志文件：`/data/mHook/<测试包名>/log.txt` 正常生成

## 4. 已知限制

- 脱壳仅对 `dumpDir` 存在的包生效
- 加固壳若在 LSPosed hook 安装前（Application 静态块之前）就已创建内存 dex，第一层可能漏抓，需依赖第三层兜底
- ArtDexFile 内存结构读取对少数厂商定制 ROM 可能失败（try/catch 兜底，不影响主流程）
- API < 26 无 `InMemoryDexClassLoader`，第一层自动跳过（已 try/catch）
- minSdk 22，老版本系统 WebView 兼容性由系统保证
