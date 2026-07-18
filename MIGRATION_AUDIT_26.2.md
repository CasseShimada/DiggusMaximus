# Diggus Maximus — Minecraft 26.2 Fabric 迁移审计

## 文档状态

- 当前阶段：`COMPLETE`
- 最后更新时间：2026-07-18 16:37 +08:00
- 当前分支：`26.2`
- 基准提交：`f754514fe1d99eace48d3e777d7d50828c869b9b`（`f754514`）
- 当前提交/工作树：完整迁移提交 `c283f139a7d9e12eb65d233fbaee42800b0298d9` 已推送到 `origin/26.2`；beta 标签 `v1.5.9-beta.1+26.2` 固定指向该提交。发布记录随分支后续文档提交保存，未覆盖无关用户修改。
- 已完成事项：迁移实现、旧配置语义兼容层、原生 GUI、客户端/公共源码拆分、typed payload、服务端原版破坏绑定和 26.2 Mixin 迁移均已实现。深度复核缺陷均已修复；beta `1.5.9-beta.1+26.2` 已连续两次干净构建，15 个 JUnit、两套运行依赖与最终 JAR 三层扫描、带 Mod Menu 20.0.1 的完整 GameTest，以及目标整合包新隔离副本的真实玩家功能/保存/重启验证均通过。GitHub Actions 又在 Ubuntu/Java 25 上从 beta 标签重新构建，并成功创建已核验的非草稿 prerelease。
- 下一项可直接执行的操作：由用户在 Minecraft 26.2 / Fabric Loader 0.19.3+ 环境安装 beta 进行日常游玩反馈；发现 Diggus 自身问题时在 `26.2` 分支修复并发布后续 beta。
- 未解决问题和阻塞项：无 Diggus 迁移或发布阻塞。目标存档/模组集自身的 Farmers Delight 缺失注册项已由用户明确排除在本模组维护范围外。

## 项目基线

### Git 与说明文件

- 初始分支：`1.19`，跟踪 `origin/1.19`；初始工作树干净。
- 预期基准与实际一致：`f754514`，提交说明 `Fix config fields not being type-able`。
- 开始时本地仅有 `1.19`，远端仅有 `origin/1.19`；已从基准 HEAD 创建本地 `26.2`，未删除或强制重建任何分支。
- 已执行并核对 `git status --short --branch`、`git log -n 10 --oneline --decorate`、`git branch --all`；仓库没有 `AGENTS.md`。
- README 仅含项目名称、标语和 CurseForge 主页；`curseforge.com` 是主页链接，不是 Forge 框架依赖。

### 结构与构建体系

- 单模块 Gradle/Fabric Loom 项目；19 个 Java 文件位于 `src/main/java`，公共资源位于 `src/main/resources`。
- 旧构建：Minecraft `1.20.1`、Yarn `1.20.1+build.2`、Fabric Loader `0.14.21`、Fabric API `0.83.1+1.20.1`、Loom `1.2-SNAPSHOT`、Gradle Wrapper `8.1.1`、Java release `17`。
- 旧直接依赖：Minecraft、Yarn、Fabric Loader、Fabric API、嵌入 JAR 的 KyrptConfig `1.5.6-1.20`、Mod Menu `7.0.1`。
- 旧传递依赖包含 Fabric API 模块、KyrptConfig 使用的 Jankson/客户端配置类及 Mod Menu 运行依赖；旧 Gradle 未能在机器唯一的 Java 25 上解析完整依赖树，因此精确的旧传递树标为未解析，而不是推测通过。
- 基线命令：`gradlew.bat --version` 显示 Gradle 8.1.1 / Oracle Java 25.0.2；`gradlew.bat clean build --stacktrace` 与 `dependencies --configuration runtimeClasspath` 均在配置阶段失败，原因为 Groovy/Gradle 8.1.1 不支持 class major 69。生产源码没有进入编译。

### 目标实例事实

- 路径：`C:\Users\Admin\AppData\Local\Programs\Minecraft_Client\PCL2\.minecraft\versions\server-26.2-loader.0.19.3`。
- `logs/latest.log` 明确记录 `Loading Minecraft 26.2 with Fabric Loader 0.19.3`、`fabric-api 0.153.0+26.2`、`java 25`、Mixin `JAVA_25`、服务端 `Done`、保存、停止。
- 启动器：`fabric-server-mc.26.2-loader.0.19.3-launcher.1.1.1.jar`；`run.bat` 当前通过 PATH 的 Java 启动，PATH 为 Oracle JDK `25.0.2`。
- 当前 mods 含 Fabric API 及 Aether、Carry On、Kaleidoscope Cookery、Twilight Forest；不存在 Diggus Maximus JAR，不得覆盖这些模组。
- 本地已验证的 26.2 构建样本使用 Java 25、Loom `1.15.5` 或更高、Gradle `9.4.0`、Loader `0.19.3`、Fabric API `0.153.0+26.2`，且 26.2 Loom 使用 Mojang/Fabric 当前命名，不再声明旧 Yarn 依赖。
- Mod Menu `20.0.1` 已在 Gradle 缓存，POM 指向 Loader `0.19.3` 和 26.2 Fabric API 模块；可作为 `compileOnly` 客户端入口。未发现 KyrptConfig 的 26.2 缓存或兼容构建，继续使用会形成核心硬依赖和客户端类加载风险，迁移将替换为 JDK/Minecraft/Fabric 原生实现。

### Entrypoint、Mixin、资源和端边界

- 旧公共入口为 `DiggusMaximusMod`，旧客户端入口为 `DiggusMaximusClientMod`，旧 Mod Menu 入口为 `config.modmenu.ModMenuIntegration`。26.2 保持这些入口类名/元数据路径，但将客户端入口和 Mod Menu 适配器物理移动到 `src/client/java`。
- 旧公共 Mixin 为 `MixinBlock`、`MixinCancelDurability`、`MixinPlayerEntity`、`MixinServerPlayerInteractionManager`；旧客户端 Mixin 为 `MixinClientPlayerInteractionManager`。26.2 公共侧改为 `MixinBlock`、`MixinCancelDurability`、`MixinPlayerEntity`、`MixinServerPlayerGameMode`，客户端侧改为 `src/client` 中的 `MixinMultiPlayerGameMode`。
- Mixin 配置 `required=true`、`defaultRequire=1`，旧 compatibility level 为 Java 17；26.2 必须保持强失败并升级 Java 25。
- 旧客户端类与公共源码混放，且公共配置类直接引用客户端按键类型；26.2 已拆分 `src/main`/`src/client`，公共配置仅保存 loader-neutral 的旧字段形态，物理服务端不解析 Minecraft 客户端类。
- 资源：`fabric.mod.json`、Mixin JSON、图标，以及 `en_us`/`pt_br`/`zh_cn` 语言文件。已有 mod ID、命名空间和语言键必须保持。

### 网络协议与数据流

- 固定通道 ID：`diggusmaximus:start_excavate_packet`。
- 旧 C2S 字段顺序：`BlockPos`、客户端方块 ID、方向整数（`-1` 表示无方向）、形状整数（`-1` 表示普通连锁）；客户端在 `breakBlock` HEAD 发送，服务器线程创建 `Excavate`。
- 旧服务器只做 `enabled` 和固定 10 格距离检查；它信任客户端方块 ID、方向和形状序号，形状越界可崩溃，且没有将请求绑定到一次通过权限/保护事件的原版破坏。这是迁移必须修复的安全缺陷。
- 26.2 方案：保留通道和四字段语义；使用 typed custom payload；服务器重新读取方块 ID、验证交互距离/已加载区块/完整状态/形状范围，忽略客户端方向并由服务端射线结果确定。所有解码请求先经过容量 8、每 tick 补充 1 的 token bucket；合法请求以 5 server tick 的未绑定 arm TTL 挂到玩家。原版 action 完整方法也由 `WrapMethod` 包裹：START 仅在原版实际追踪同一 `destroyPos` 时保留绑定，STOP 仅在同步破坏消费或同一 `delayedDestroyPos` 时保留，ABORT、拒绝、取消、异常和 tick 脱离状态均清除。只有对应的原版 `destroyBlock` 成功且状态确实变化后才连锁；其完整方法以 `try/finally` 清理重入深度，因此可取消事件/保护注入不会泄漏上下文。所有附加破坏继续调用原版 `destroyBlock`，复用权限、事件、掉落、经验、附魔和游戏模式规则。MixinExtras 0.5.4 由允许的 Fabric Loader 0.19.3 自带，项目未另行声明或嵌入第三方运行框架。

### 配置与持久化

- 旧配置根路径：Fabric config 目录下 `diggusmaximus/`。
- 文件名保持：`config.json5`、`blacklist.json5`、`grouping.json5`、`excavatingshapes.json5`。
- 键保持：`enabled`、`keybinding`（含 `rawKey` 语义）、`invertActivation`、`sneakToExcavate`、`mineDiag`、`maxMinedBlocks`、`maxMineDistance`、`autoPickup`、`requiresTool`、`dontBreakTool`、`stopOnToolBreak`、`toolDurability`、`playerExhaustion`、`exhaustionMultiplier`、`tools`、`isWhitelist`、`blacklistedBlocks`、`customGrouping`、`groups`、`enableShapes`、`includeDifBlocks`、`shapeKey`、`cycleKey`、`selectedShape`。
- 默认值来自源码：enabled/mineDiag/autoPickup/toolDurability/stopOnToolBreak/dontBreakTool/playerExhaustion 为 true；最大数量 40、距离 10、倍率 1.0；普通激活键 grave accent 且 unknown 表示始终激活；形状/循环键 unknown 但 unknown 表示未激活；默认形状 LAYER；列表为空；其余布尔为 false。
- 全源码未注册方块、物品、实体、菜单、配方、注册表别名、玩家 NBT、世界 `PersistentState`/`SavedData` 或自定义存档数据；唯一持久写入是上述全局配置文件。当前最终 JAR 已在目标实例完整新隔离副本中完成真实玩家连接、潜行连锁、保存/停止和重启持久化检查；原目标 `world\level.dat` 前后 SHA-256 均为 `0E6514A6BE77A36B6706AB82EA30A07AB6B9A146D4A19A4FA55C8123C5717CED`。

## 完整功能清单

- 普通连锁挖掘：以原始方块 ID 为匹配基准，BFS/队列扩散；原始方块计入 `maxMinedBlocks`。
- 激活：客户端激活键；`invertActivation` 只反转普通激活键；无效键不触发；普通键为 unknown/空键时 `unknownIsActivated=true`，即始终触发。
- 纯服务端激活：`sneakToExcavate=true` 时，潜行完成一次原版方块破坏后触发，不要求客户端安装模组。
- 普通扩散：`mineDiag=false` 使用 6 个正交邻居；true 使用 3×3×3 内除原点外的 26 个邻居。形状模式不读取此开关。
- 边界：最大数量配置 UI 范围 1..2048；最大欧氏距离 UI 范围 1..128；旧代码内部距离使用配置值 `+1` 再钳制到 128。
- 黑名单/白名单：`isWhitelist == lookup.contains(blockId)` 时允许；条目支持方块 ID 和 `#tag` 展开。
- 自定义分组：每个逗号分隔组把后续 ID/标签展开结果映射到组首，使不同方块按同一 ID 匹配。
- 形状：HORIZONTAL_LAYER、LAYER、HOLE、ONExTWO、ONExTWO_TUNNEL、THREExTHREE、THREExTHREE_TUNNEL；根据命中面决定水平/垂直层和隧道方向；`includeDifBlocks=true` 时形状忽略方块 ID。
- 形状按键：独立形状激活键；循环键向前切换，潜行时反向，动作栏显示所选形状并保存。
- 工具：`requiresTool` 要求物品具有 `DataComponents.MAX_DAMAGE`（包括不可破坏但仍是工具的物品）或 ID 在 `tools`；开始后主手物品类型改变时，`stopOnToolBreak` 或 `requiresTool` 可停止。
- 耐久：正常通过原版工具破坏扣耐久；`toolDurability=false` 时只在 excavating 期间取消；`dontBreakTool=true` 时最后一点耐久前停止；创造模式绕过工具/耐久停止检查。
- 饥饿：普通破坏保持原版；连锁期间 `playerExhaustion=false` 取消每块 0.005 exhaustion，否则乘 `exhaustionMultiplier`。
- 掉落/经验：每块通过原版服务器破坏流程，保留战利品、附魔、方块实体处理、统计与经验；`autoPickup=true` 按旧版可见时序查询方块位置 AABB 并插入玩家背包，未放入的余量留在世界。客户端包路径在匹配的完整 `destroyBlock` wrapper 入口（种子原版破坏前）捕获种子位置既有实体 UUID，而不是在收到自定义包时长期持有快照；因此只拾原版破坏前已有实体，不提前吸取种子的新掉落。纯服务端潜行路径在原版破坏后查询全部实体；经验保持原版生成/拾取。
- 安全/兼容：不可破坏硬度、创造/生存、原版限制、Fabric `PlayerBlockBreakEvents`/保护模组均由原版 destroy 流程决定；`WrapMethod` + `try/finally` 保证取消/异常/同步重入不会泄漏上下文，队列、visited、最大数量、距离、请求 TTL、状态绑定与 token bucket 共同限制滥用。
- 环境：单人游戏使用集成服务器；局域网/独立服务器使用服务器配置和客户端按键请求；纯服务端潜行模式允许无客户端模组；配置界面和 Mod Menu 只在客户端。

## 功能等价矩阵

| 功能 | 旧实现位置 | 旧行为 | 26.2 新实现/迁移方法 | 测试方法 | 状态 | 证据/日志 |
|---|---|---|---|---|---|---|
| 普通连锁、对角线 | `Excavate`, `ExcavateTypes` | 6/26 邻居 BFS | 保留偏移集合，增加 visited 与有界队列 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 集成服和客户端连接的独服均清除 3×3 同类方块；邻域/形状单测通过 |
| 数量/距离 | `ExcavateHelper` | 2048/128 钳制，距离 +1 | 服务端钳制并逐块验证 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 实际验证最大数量、欧氏距离、shape+sneak 共享上限均未被绕过 |
| 黑/白名单、标签 | `Blacklist` | ID/tag 展开 | 原生 registry/tag holder；tags loaded 后刷新；每个候选块复验 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 黑名单、白名单、标签及跨方块形状路径均按每块配置裁决 |
| 自定义分组 | `BlockCategory` | 逗号组及 tag 展开 | 保留“后续项映射到组首”及重叠组行为 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 重叠组单测和 stone/dirt 实际分组连锁通过 |
| 七种形状/方向/切换 | `ExcavateTypes`, client tick | 方向扩散、潜行反向循环 | 保留枚举/几何，服务端重新射线确定方向 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 7/7 几何单测、七种服务端代表路径及正反循环按键均通过 |
| 激活/反转/unknown | `DiggusKeyBinding` | 空串/显式 unknown 按 `unknownIsActivated`；非法键不触发；unknown 在反转前返回 | `src/client` 原生 `KeyMapping` 包装并保留旧 `rawKey` | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 真实客户端覆盖非法字符串、显式 unknown、普通按键和 invert 顺序 |
| 纯服务端潜行 | 旧 `MixinServerPlayerInteractionManager` | 成功原版破坏后触发 | `MixinServerPlayerGameMode` 在成功原版破坏后触发，独立于 client-packet enabled | 14:47 客户端 GameTest | `PASS` | 关闭 packet enabled 后，仅潜行仍清除种子与相邻块；shape+sneak 双阶段也通过 |
| 工具/自定义工具 | `ExcavateHelper` | damageable 或 tools ID | `DataComponents.MAX_DAMAGE` 或配置 registry ID | 14:47 生存 GameTest | `PASS` | 空手拒绝、配置 stick 接受、不可破坏钻石镐仍识别为工具 |
| 耐久三开关 | ItemStack Mixin/helper | 可取消、最后一点、损坏后停止 | 26.2 `hurtAndBreak` Mixin + `TOOL.damagePerBlock` 逐块预算 | 14:47 生存 GameTest | `PASS` | 默认精确扣耐久、关闭耐久、damagePerBlock=2、dontBreakTool、stopOnToolBreak true/false 均通过 |
| 饥饿/倍率 | Block Mixin | 连锁取消或乘倍率 | `Block.playerDestroy` 调用点 Redirect | 14:47 生存 GameTest | `PASS` | `playerExhaustion=false` 保留仅种子 0.005；倍率 3 实测为 0.035 |
| 原版掉落/经验/事件 | `tryBreakBlock` | 原版破坏，旧实现可能重复 BEFORE | 每个附加方块只调用原版 `destroyBlock`；完整方法 wrapper 防取消泄漏 | 14:47 生存 GameTest | `PASS` | 普通掉落/XP、Silk Touch、Fortune、Fabric BEFORE 精确一次/取消和同步重入均通过 |
| 自动拾取 | `pickupDrops` | AABB 插背包，余量留地面；包触发在种子破坏前，纯潜行在之后 | 匹配的完整 `destroyBlock` wrapper 入口捕获既有 UUID；部分余量保留；双阶段共享上限 | 14:47 生存 GameTest | `PASS` | 背包仅余 1 个容量时库存 63→64，另外 2 个圆石留在世界且三块均破坏 |
| 网络合法性 | `StartExcavatePacket` | 弱校验 | typed payload；5-tick arm、原版 action 状态机所有权、ABORT/主动清理、完整状态、全请求 token bucket | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 非法形状/ID、过期、同 ID 状态改变、取消尝试、百万格远端 action 不加载区块、未加载/超距和世界边界均未触发连锁 |
| 配置与原生 GUI | 旧配置库 GUI | 四 JSON5；按键保存为字符串 | 内置 JSON5 语义兼容层 + `src/client` 原生 Screen/O 键入口 | 15 JUnit + 14:47 客户端 GameTest | `PASS` | 旧字符串/对象、缺字段、未知字段、错误不覆盖、边缘数值/转义、Done 保存、Esc 丢弃和 320px 布局均通过 |
| 可选 Mod Menu 入口 | `ModMenuIntegration` | 从 Mod Menu 打开配置界面 | `src/client` 中 `clientCompileOnly` 适配同一原生 Screen | 16:27–16:28 beta + Mod Menu 20.0.1 完整 GameTest | `PASS` | 实际加载 Mod Menu 20.0.1，并在客户端线程调用 entrypoint factory，返回同一原生 `DiggusConfigScreen`；完整功能套件随后通过 |
| 单人/局域网/独服 | 入口/网络 | 客户端请求+服务端执行 | `src/main`/`src/client` 环境拆分 | 14:47 集成服 + dedicated connection GameTest | `PASS` | 集成服完整套件通过；独服出现真实 login/join、执行 3×3 连锁并干净保存停止 |
| 存档兼容 | 无自定义世界数据 | 只影响原版破坏 | 保持 mod ID/无注册表变化 | 当前最终 JAR 的真实目标副本 | `PASS` | 48 模组目标整合包加载；真实玩家 join 后潜行破坏种子及相邻方块，保存/停止/重启后两个方块仍为空；原目标哈希不变 |

## API 迁移矩阵

| 1.20.1 API / Mixin 目标 | 旧位置 | 26.2 替代方案 | 状态 | 证据 |
|---|---|---|---|---|
| Yarn `net.minecraft.util.Identifier` | 多处 | 26.2 `net.minecraft.resources.Identifier` | `PASS` | 15 个 JUnit 与 14:47 客户端/独服运行均完成类加载和实际 ID 比较 |
| Yarn `Registries` | 多处 | `BuiltInRegistries` | `PASS` | 黑白名单、分组、工具 ID 和网络状态在当前 GameTest 中实际运行 |
| `ServerPlayerInteractionManager.tryBreakBlock` | `Excavate` | `ServerPlayerGameMode.destroyBlock` | `PASS` | 生存、创造、事件取消、附魔和独服 GameTest 均通过 |
| `finishMining` Redirect | 旧 `MixinServerPlayerInteractionManager` | `MixinServerPlayerGameMode` 的 `WrapMethod(destroyBlock)` + 深度 `try/finally` | `PASS` | 当前 Mixin runtime 通过；取消路径与同步重入回归均通过 |
| `ClientPlayerInteractionManager.breakBlock` | 旧 `MixinClientPlayerInteractionManager` | `src/client` 的 `MixinMultiPlayerGameMode.destroyBlock` | `PASS` | 真实客户端键鼠触发普通/形状 payload 并完成连锁 |
| `Block.afterBreak`/`PlayerEntity.addExhaustion` | `MixinBlock` | `Block.playerDestroy`/`Player.causeFoodExhaustion` | `PASS` | 当前 GameTest 实测关闭值和 3 倍倍率 |
| `ItemStack.damage(int, Random, ServerPlayer)Z` | `MixinCancelDurability` | `ItemStack.hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer)` | `PASS` | 当前 GameTest 实测默认、关闭、最后耐久及每块 2 点伤害 |
| 原始 `PacketByteBuf` receiver | 网络 | `CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry` | `PASS` | 当前集成服/独服真实连接、恶意字段拒绝及 pending 单测通过 |
| `InputUtil`/旧按键包装 | 客户端 | `src/client` 的 `InputConstants` + 原生 `KeyMapping` | `PASS` | 14:47 当前客户端覆盖原生按键、unknown、非法键、invert、循环及 O 键入口 |
| 旧 `TagHelper` | 配置 | `TagKey` + registry holder + tags-loaded lifecycle | `PASS` | ConfigLookup JUnit 与当前真实标签名单 GameTest 通过 |
| 旧 ConfigManager/JSON5 库 | 配置 | JDK 文件 API + 内置 JSON5 兼容解析器 | `PASS` | 当前 JUnit 覆盖 fixture、缺字段、未知字段、错误不覆盖、保存重载和边缘 JSON5 值 |
| 旧配置 GUI | 客户端配置 | 原生 Minecraft `Screen`/`Button`/`EditBox` | `PASS` | 当前 GUI GameTest 覆盖 O 键打开、Done/Esc、重载和窄屏布局 |
| Mod Menu 7 API | 可选客户端入口 | Mod Menu 20.0.1 `clientCompileOnly` 适配 | `PASS` | 最终完整 Client GameTest 实际加载 20.0.1，并在客户端线程断言 entrypoint factory 返回原生配置屏 |

## 框架清除审计

- 最终生产源码/资源/构建脚本扫描覆盖 `net.minecraftforge`、`net.neoforged`、mods/neoforge TOML、DeferredRegister、EventBus、Capability、ForgeConfig、Architectury、Forgified、Sinytra/Connector、Porting Lib、旧配置库类名和 Jankson，真实匹配数 `0`；`.gitignore` 的旧模板标题也已中性化。仅 `fabric.mod.json` 的 CurseForge 主页按要求排除。
- 最终 `runtimeClasspath` 与 `clientRuntimeClasspath` 均解析为 Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.153.0+26.2 及其 Loader/Minecraft 运行库；禁用框架/桥接/旧配置库匹配数均为 `0`，Mod Menu 坐标匹配数也均为 `0`。MixinExtras 0.5.4 是 Loader 0.19.3 自带的 Mixin 扩展，不是项目另加的桥接或运行框架。
- 最终 remap JAR 共 50 个条目、无嵌入 JAR、无 test/gametest 类或元数据；路径扫描和解压后二进制/文本扫描的禁用框架匹配数均为 `0`。展开后的 `fabric.mod.json` 精确约束 Minecraft `=26.2`、Loader `>=0.19.3`、Fabric API `>=0.153.0+26.2`、Java `>=25`。
- 三层最终结论：源码/构建 `PASS`、解析后运行依赖 `PASS`、最终产物 `PASS`。

## 存档和配置兼容性

- mod ID `diggusmaximus`、资源命名空间、四个 JSON5 文件路径、全部旧配置键/默认值、枚举常量和语言键均保持。
- 兼容解析器已通过注释语法、未引号键、单双引号、尾随逗号、`\\xHH`、超大整数/十六进制、高精度小数、嵌套 keybinding 对象和列表测试；缺字段取旧默认，未知字段值随保存保留，解析失败记录错误并禁止覆盖原文件。
- “兼容”指配置键、类型、值、默认值、列表/嵌套结构和未知字段的语义保持，不承诺字节级文本保持。成功保存会用规范化 JSON5 重新序列化整个文档，因此原注释、空白、缩进、单双引号选择、未引号键和尾随逗号等格式会被规范化；这是已披露的文本格式变化，不是静默重置配置值。
- 保存先写同目录临时文件再原子替换（不支持时普通替换）。当前 JUnit fixture 实测 `rawKey`、数值及根/嵌套/黑名单/分组未知字段在保存和第二次重载后仍保持；缺失的形状配置按旧默认创建为 `LAYER`。
- 源码证明无世界/玩家自定义持久数据和注册表对象。当前最终产物已在目标整合包新隔离副本中取得真实玩家 login/join、真实窗口 Shift+左键潜行连锁、保存/停止和重启持久化证据。
- 最终隔离目录：`C:\Users\Admin\AppData\Local\Programs\Minecraft_Client\PCL2\.minecraft\versions\server-26.2-loader.0.19.3\codex-diggusmaximus-final-20260718-150235`；权威证据位于其 `evidence` 目录的 `status.json`、`server-pass1.latest.log`、`client.latest.log` 和 `server-pass2.latest.log`。历史隔离目录 `codex-diggusmaximus-target-20260717-0106` 仅保留旧产物证据，不参与最终结论。

## 最终验证汇总（2026-07-18 当前产物）

- 构建与单测：beta 版本与发布工作流完成后，16:26–16:27 连续两次独立 `gradlew.bat clean build --no-daemon --console=plain` 均成功，10/10 任务实际执行，均约 13 秒；6 个 JUnit XML 合计 15 个测试，失败/错误/跳过均为 0。仅有 Gradle 10 未来兼容、Java 25 native-access/Unsafe 和测试 API deprecation 提示。
- 客户端/独服：16:27–16:28 最终 `runClientGameTest` 实际加载 `diggusmaximus 1.5.9-beta.1+26.2`、Minecraft 26.2、Loader 0.19.3、Fabric API 0.153.0+26.2、Java 25、MixinExtras 0.5.4 和 Mod Menu 20.0.1；Mod Menu entrypoint factory、配置 GUI、真实按键、全部挖掘/工具/耐久/掉落/安全回归、集成服，以及真实客户端连接临时独服并执行 3×3 连锁均通过。Gradle 退出码 0、`BUILD SUCCESSFUL`，最终日志为 `build/run/clientGameTest/logs/latest.log`（SHA-256 `8E9D6BEA3C6D405E163549265203646FE8C81373822D0B6F701D2A1315232429`）。离线认证/Realms 401 与本机旧 `Anisotropic Filtering=0` 警告不影响结果。
- 目标世界：最终 JAR 随目标整合包共 48 个模组加载到 `Done`；真实离线玩家 `DiggusVerify` 出现 login/join，实体及区块检查通过，真实窗口 Shift+左键后种子 `(11,200,11)` 和相邻 `(12,200,11)` 均由 stone 变为 air。`save-all flush`、五维度保存和干净停止通过；新进程重启后两个位置仍为空，再次保存并干净停止。
- 目标存档对照：玩家加载邻近区段时出现 Farmers Delight `wild_beetroots`/`sandy_shrub` 缺失并回退默认值。为区分迁移回归与原数据问题，在另一个同源隔离副本中暂时移出 Diggus JAR，仅加载其余 47 个目标模组并强制加载相同区块，仍独立复现 6 条相同警告；退出码 0、全部维度保存并干净停止，随后 JAR/属性已恢复且无遗留进程。因此目标整包本身不是 registry-clean，但该问题不是 Diggus 引入，不能作为本模组迁移造成数据丢失的证据。
- 原目标安全：目标原件未启动或写入；其 `world\level.dat` 在最终隔离验证前、验证后和收尾复核时 SHA-256 均为 `0E6514A6BE77A36B6706AB82EA30A07AB6B9A146D4A19A4FA55C8123C5717CED`。验证脚本只清理自己记录且命令行精确指向隔离目录的 PID。
- 产物与清除扫描：最终 beta `diggusmaximus-1.5.9-beta.1+26.2.jar` 为 96,109 字节，SHA-256 `1F4F78A2F446058358627358A702F4A9BAB6435638708A0086FBF842D103CB6E`；50 个条目、0 嵌入 JAR、0 test/gametest 条目、路径和文本禁用框架匹配均为 0。生产源码/资源/构建脚本/发布工作流匹配为 0；最终 `runtimeClasspath` 与 `clientRuntimeClasspath` 的禁用框架和 Mod Menu 运行坐标匹配也均为 0。与已完成目标世界验证的稳定元数据 JAR 逐条比较时，除 `fabric.mod.json` 版本字段外的 49 个条目内容哈希差异为 0。

## 此前验证汇总（2026-07-18 修复前，仅作历史证据）

> 本节记录的 JAR 哈希、测试数量和日志属于本轮缺陷修复前产物，只能作为历史证据；当前权威结论见上一节，不得将本节旧产物描述为最终产物。

- 构建：在所有生产改动和清除扫描修订后连续执行两次 `gradlew.bat clean build`，两次均成功且 10 个任务实际执行；JUnit XML 共 8 个测试、0 失败。仅有 Gradle 10 未来兼容提示及 Gradle/JOML 在 Java 25 下的 native-access 提示。
- 自动客户端：`gradlew.bat runClientGameTest` 最终成功。原生配置界面保存/重载、集成服创造普通连锁、集成服生存掉落/经验/精确耐久、客户端连接独服并连锁均通过。离线测试环境出现预期的认证/Realms 401 和本机旧选项 `Anisotropic Filtering=0` 警告，不影响测试结果。
- 客户端 smoke：不安装 Mod Menu 时 Minecraft 26.2 客户端初始化成功；临时加入 Mod Menu 20.0.1 时也初始化成功，且无 entrypoint、Mixin 或类加载错误。
- 服务端 smoke：纯 Fabric API + Diggus 的开发独服完成 `Done`、保存和干净停止；目标整合服隔离副本加载 Aether、Carry On、Cookery、Twilight Forest、Fabric API 与本模组，连续两次 `Done`/保存/停止，进程退出码均为 0、问题扫描均为 0。
- 发现并修复：目标副本首轮发现 registry tag 在绑定前被配置派生集合读取；现改为先加载直接 ID，再在 `CommonLifecycleEvents.TAGS_LOADED` 刷新 tag/grouping，并增加未绑定 tag 回归测试。修复后的目标服连续两轮通过。
- 当时产物：`build/libs/diggusmaximus-1.5.9+26.2.jar`，大小 `84887` 字节，SHA-256 `E273C4198E283FA17B605EF76BC13481C151ED32D565AEE7195FD29A91A5E1C5`；已因后续源码修复失效并被下述本轮最终产物替换。
- 关键日志：目标副本 `diggus-pass1.log`、`diggus-pass2.log`；客户端 GameTest `build/run/clientGameTest/logs/latest.log`。

## 已知风险与剩余手工扩展项

- 当前自动测试已覆盖七种形状几何、七种服务端代表路径及正反循环，但没有在真实可见窗口中逐一人工操作所有形状与全部命中朝向；这是发布前手工补充项，不推定为已通过。
- 当前生存 GameTest 已覆盖默认耐久、`toolDurability=false`、`dontBreakTool`、`stopOnToolBreak` true/false、`TOOL.damagePerBlock=2`、配置自定义工具、不可破坏工具、掉落、经验、Silk Touch 和 Fortune；未穷举第三方模组工具在运行时主动替换主手物品的所有组合。
- 当前自动拾取 GameTest 已覆盖背包只剩 1 个可接收数量时的部分余量落地；尚未穷举实体被其他模组在同一破坏回调中移动/合并的交互。
- 纯服务端潜行逻辑已在关闭 client-packet enabled 的真实服务器路径中通过，但尚未用一个完全不安装客户端模组的真人客户端执行该动作。
- Fabric BEFORE 精确一次、取消和同步重入已自动通过；尚未安装额外领地/保护模组做交叉测试。实现以完整 `WrapMethod` 包裹每个原版 `destroyBlock`，但第三方组合仍属于发布前 smoke 范围。
- JSON5 配置的语义兼容已通过，但成功保存会规范化注释和文本格式；管理员若要求保留手写注释，应在首次保存前备份原文件。
- 目标世界原数据在玩家附近的部分区段报告 Farmers Delight `wild_beetroots`/`sandy_shrub` 注册项缺失并使用默认值；不加载 Diggus、仅加载其余 47 个目标模组的同源对照副本强制加载相同区块时独立复现 6 条同类警告，支持判断这是目标整合包/存档自身的不一致。服务器未崩溃，Diggus 功能、保存和重启均继续通过。用户于 16:17 明确指示无需处理 Farmer’s Delight、只维护本模组；因此不安装该模组、不修改目标实例，并把该外部问题排除在 Diggus 迁移验收范围外。
- 本次要求的阻塞验收项均已通过；上述项目是发布前可继续扩展的组合/人工 smoke，不阻止当前阶段为 `COMPLETE`。

## 工作日志

### 2026-07-17 00:30 +08:00 — 启动与 Git 安全基线

- 修改：从干净的 `f754514` 创建并切换本地 `26.2`；创建本审计文档。
- 原因：满足迁移分支和单一事实来源要求，并在修改生产代码前固化基线。
- 影响：仅分支与 Markdown；未修改生产源码。
- 命令：Git 状态/日志/分支命令、README/构建/目标目录只读检查。
- 结果：分支、基准和旧版本与预期一致；无 `AGENTS.md`。

### 2026-07-17 01:05 +08:00 — 初始审计完成并进入迁移

- 修改：回填完整基线、功能清单、功能/API 矩阵、框架与兼容性结论；阶段从 `AUDIT` 经 `AUDIT_COMPLETE` 立即改为 `MIGRATION`。
- 原因：全部生产源码、资源、构建文件和相关历史已阅读；已取得目标实例和 26.2 本地 API 证据。
- 影响：仍仅审计文档。
- 命令：`rg --files`/全源码读取/Forge 模式扫描；Git 历史；`gradlew --version`、基线 `clean build`、依赖命令；目标 mods/config/logs/Java 检查；对本地 26.2 Minecraft JAR 执行 `jar tf`/`javap`；核对同机已成功 26.2 Fabric 项目。
- 结果：旧基线因 Gradle 8.1.1 + Java 25 在配置阶段失败；确认目标是 Java 25/Loader 0.19.3/API 0.153.0；发现旧数据包信任边界和客户端类混放风险；源码/资源/构建初扫无 Forge/NeoForge/桥接引用。
- 下一步：升级构建并实施原生配置/GUI、typed payload、成功原版破坏绑定、26.2 Mixin 及环境拆分。

### 2026-07-17 01:15 +08:00 — 26.2 原生迁移单元完成

- 修改：构建升级到 Minecraft 26.2、Loader 0.19.3、Fabric API 0.153.0、Java 25、Loom 1.15.5、Gradle 9.4；删除 KyrptConfig/Jankson 运行依赖；增加保留四个旧路径/键/未知字段的 JSON5 兼容层与 Minecraft 原生 GUI；Mod Menu 改为 20.0.1 `clientCompileOnly` 可选适配；拆分 `src/main`/`src/client`；网络改为 typed payload 并绑定成功原版破坏；全部 26.2 Mixin 目标按本地字节码迁移。
- 原因：满足 Fabric 原生、物理服务端安全、旧配置兼容和不信任客户端数据的要求。
- 影响：构建脚本、Wrapper、fabric 元数据、全部运行逻辑、Mixin、客户端按键/配置界面；mod ID、命名空间、四个配置文件、配置键、通道 ID、枚举名和已有语言键不变。
- 命令：`gradlew compileJava compileClientJava`、`gradlew test`、`gradlew clean build --stacktrace`；编译错误只出现过一次（26.2 `EditBox` 删除 `setFilter`），已按实际签名修正。
- 测试：首次 `clean build` 通过；6 个 JUnit 测试通过，覆盖 JSON5 注释/未引号键/单双引号/尾逗号/嵌套未知字段、旧值与缺字段默认、不可读文件不覆盖、保存重载、七种形状几何、非法形状回退、待处理网络请求的位置/方块/时效绑定。
- 下一步：生成依赖/JAR 三层扫描证据，物理服务端 Mixin smoke，第二次干净构建，客户端与旧配置/存档副本验证。

### 2026-07-17 01:26 +08:00 — 真实运行与目标实例隔离验证

- 修改：加入 Fabric Client GameTest；建立目标实例完整隔离副本并放入本次产物；用旧 JSON5 fixture 和真实世界副本验证。
- 测试：开发独服完成加载/保存/停止；无 Mod Menu 和有 Mod Menu 两种客户端完成初始化；GameTest 驱动集成服与独服；目标整合服执行两轮启动/保存/停止。
- 发现：目标副本首轮在 registry tags 绑定前读取 tag，触发未绑定异常。测试开发过程中还先后发现等待时间不足、物品未选中和石质测试地板被连锁挖掉导致玩家坠落，均属于测试夹具问题。
- 修复：配置派生 tag/grouping 改为 `TAGS_LOADED` 后刷新并容忍绑定前状态；游戏夹具改为动态等待、明确选中工具和基岩地板。修复后回归全部通过。
- 安全：原目标实例未修改；原 `world\level.dat` 哈希保持；验证目录保留供复核。

### 2026-07-17 01:36 +08:00 — 当时的构建、清除审计与交付收尾（结论后被复核撤销）

- 修改：清除生产注释中残余的旧库名；补齐审计矩阵、验证证据和已知风险。
- 命令：连续两次 `gradlew.bat clean build`；`runClientGameTest`；生产/测试源码扫描、两套 runtime classpath 扫描、最终 JAR 路径及字节扫描；`git diff --check`。
- 结果：两次干净构建成功，8 个 JUnit 测试通过；客户端 GameTest 通过；三层框架清除扫描均为 0；最终 JAR 不含测试类；`git diff --check` 仅报告现有 Windows 行尾转换提示，无空白错误。
- 产物：`diggusmaximus-1.5.9+26.2.jar`，SHA-256 `E273C4198E283FA17B605EF76BC13481C151ED32D565AEE7195FD29A91A5E1C5`。
- 当时状态曾标记 `COMPLETE`；2026-07-18 深度复核发现行为与验证缺口后已撤销为 `MIGRATION`，下列新工作日志为当前事实。

### 2026-07-18 13:27 +08:00 — 深度复核缺陷修复与回归扩充

- 修改：旧按键字段保持字符串或原预览对象形态；纠正 unknown/非法键/反转语义；待处理请求绑定维度、完整 `BlockState`、方块 ID、位置和 20 tick 时效；请求及每个附加方块复验交互距离、加载区块、建造高度、世界边界/权限和名单；破坏 Mixin 用栈保存重入上下文及原始工具；工具耐久按 `DataComponents.TOOL.damagePerBlock` 估算；自定义分组恢复旧版组首映射规则；GUI 使用草稿，仅 Done 保存，Esc 丢弃并恢复暂停语义。
- 自动拾取/组合触发：包路径记录种子 AABB 既有实体 UUID；纯潜行保持破坏后全量查询；shape+潜行在一个 `Excavate` 中分两阶段执行，恢复旧可见行为、第二阶段拾取种子新掉落，并共享 `visited`/`mined`，不得突破 `maxMinedBlocks`。
- 测试：扩充旧配置字符串/缺字段/未知字段/不可读文件、按键、完整 pending、重叠分组单测；客户端 GameTest 新增 Done/Esc、所有形状代表路径、数量/距离、逐块黑白名单/tag/分组、工具和耐久组合、部分背包自动拾取、纯服务端潜行、shape+sneak、非法/过期/状态不匹配/超距离包、世界边界。
- 命令：`gradlew.bat test compileClientJava compileGametestJava --stacktrace`。
- 结果：JUnit、生产客户端源码和扩充后的 GameTest 源码编译全部成功；真实 GameTest、两次干净构建、三层扫描和目标隔离副本仍待本轮最终重跑。
- 下一步：补 JSON5 合法边缘值与无 Mod Menu GUI 入口，再执行真实运行验证。

### 2026-07-18 13:44 +08:00 — 首次扩充客户端回归及夹具修正

- 修改：补 JSON5 `\\xHH`、超大整数/十六进制和高精度小数类型保持；对象式按键缺少 `rawKey` 自动补默认；增加无需 Mod Menu 的原生 O 键配置入口；GameTest 增加饥饿倍率、Silk Touch/Fortune、Fabric BEFORE 取消、冒险模式、shape+sneak 饱和上限等场景。
- 命令：`gradlew.bat test compileClientJava compileGametestJava --stacktrace`、`gradlew.bat runClientGameTest --stacktrace`。
- 结果：当时 14 个 JUnit、客户端及 GameTest 编译通过。首次真实客户端运行正常加载 Minecraft 26.2/Loader 0.19.3/API 0.153.0、Mixin `JAVA_25` 并进入集成世界；在测试框架的 `pressKey` 未触发新增配置键计数处超时，尚未执行后续场景。这是入口测试注入方式问题，不是运行崩溃或 Mixin 失败。
- 修正：入口测试改为调用原生 `KeyMapping.click(defaultKey)` 注入一次按键点击，仍由生产 tick 回调消费并打开界面；修正后已在 14:47 最终完整回归中通过。

### 2026-07-18 14:22 +08:00 — 生命周期加固后的真实回归失败（后续已修复）

- 修改：待处理请求改为 5 server tick 的未绑定 arm TTL，并在原版 `START_DESTROY_BLOCK`/`STOP_DESTROY_BLOCK` 上绑定、`ABORT_DESTROY_BLOCK` 上清除；增加每玩家容量 8、每 tick 补充 1 的 token bucket、同 tick 请求合并及服务端 tick 主动清理；种子拾取 UUID 改在匹配 `destroyBlock` HEAD、即原版破坏前捕获。
- 命令：`gradlew.bat runClientGameTest --no-daemon --console=plain`。
- 结果：Minecraft 26.2、Loader 0.19.3、Fabric API 0.153.0+26.2、Java 25 和全部 Mixin 正常加载；配置、原生按键、窄屏 GUI、普通/生存连锁、数量/距离、七形状、名单/标签/分组、工具/耐久、饥饿、Silk Touch/Fortune 和 Fabric BEFORE 取消均已运行到通过。新增同步重入场景失败：种子与嵌套方块为空，但种子上方相邻方块仍为石头，证明嵌套原版破坏改变了外层请求行为。
- 证据：`build/run/clientGameTest/logs/latest.log` 中 `seed=Block{minecraft:air}, extra=Block{minecraft:stone}, nested=Block{minecraft:air}`；本次退出码 1，不能作为通过证据。
- 下一步：用破坏上下文/请求生命周期诊断日志定位 pending 丢失位置；修复后从头重跑，不会跳过或弱化该断言。最终依赖/JAR/目标世界证据仍必须基于修复后的新产物。

### 2026-07-18 14:34 +08:00 — 取消/重入清理修复与完整客户端回归通过

- 根因：Fabric `PlayerBlockBreakEvents.BEFORE` 的可取消注入会生成提前返回；旧 `destroyBlock` HEAD/RETURN 字段栈在事件拒绝路径没有执行对应 RETURN，泄漏一层上下文。后续同步重入及自动拾取场景因此被误判为嵌套调用并跳过外层请求。
- 修复：以 Loader 自带 MixinExtras 的 `@WrapMethod` 包裹已经混入事件/保护逻辑的完整原版 `destroyBlock`；每次调用的方块状态、工具和种子实体集合改为 Java 局部变量，以深度计数抑制同步嵌套和 excavation 自身递归，并在 `finally` 中无条件恢复深度。取消尝试仍消费其精确匹配请求，只有返回成功且方块状态实际变化才连锁。
- 安全加固：`handleBlockBreakAction` 在任何 `getBlockState` 前先用 pending 自带维度/坐标、建造高度及 `hasChunkAt` 拒绝无关 START/STOP，ABORT 直接清理；避免恶意原版 action 在原版校验前触碰远端未加载区块。测试源码加入远端 action 不加载区块且不保留请求的回归。
- 命令：`gradlew.bat test compileClientJava compileGametestJava --no-daemon --console=plain`、`gradlew.bat runClientGameTest --no-daemon --console=plain`。
- 结果：当时 14 个 JUnit 及生产/客户端/GameTest 源码编译成功；完整 GameTest 于 14:31–14:33 成功，覆盖配置 GUI/原生入口、真实键位、集成服全部功能组合、事件取消后的重入和自动拾取、非法请求、世界边界，并由真实客户端连接临时 dedicated server 完成连锁；退出码 0，服务器保存并干净停止。
- 失败尝试说明：14:29 一次主代理诊断启动与并行只读代理正在运行的同一 GameTest 重叠，`deleteGameTestRunDir` 因日志文件占用而在 Minecraft 启动前失败；无生产逻辑执行或文件损坏。并行运行随后停止，之后所有 Gradle/Java 运行均由主代理串行调度。
- 证据：`build/run/clientGameTest/logs/latest.log`；最终两次 clean build 会清除此路径，故完成构建后必须再运行一次以保留最终日志。
- 下一步：最终双构建、依赖/JAR 扫描、再跑最终 GameTest，并用最终 JAR 建立目标整包世界新隔离副本做真实玩家进入/功能/保存/重启验证。

### 2026-07-18 14:47 +08:00 — 原版破坏状态机所有权加固与最终完整回归

- 复核发现：仅用固定 pending TTL 无法同时证明慢速挖掘合法且绑定后的请求不会无限存活；因此把 `handleBlockBreakAction` 和服务端 tick 都改为完整方法 wrapper，以原版 `destroyPos`/`delayedDestroyPos` 是否仍精确跟踪同一位置作为唯一所有权条件。
- 修复：未绑定请求只可 arm 5 tick；START 只有在原版新建精确跟踪后保留，STOP 只有在同步破坏已消费或原版仍精确延迟跟踪时保留，ABORT、拒绝、取消、异常及原版停止跟踪均清理。所有解码请求在任何拒绝分支前消费 token；在读取方块状态前先验证维度、坐标、建造高度及区块已加载。
- 测试：新增合法 pending 后发送相距 1,000,000 格的恶意原版 START，断言远端区块未加载且请求已清理；并重跑此前全部配置、GUI、按键、形状、名单、工具/耐久、掉落/经验、自动拾取、事件取消/同步重入、纯潜行、shape+sneak、网络滥用、集成服与真实客户端连接独服场景。
- 命令：`gradlew.bat test compileClientJava compileGametestJava`、`gradlew.bat runClientGameTest`。
- 结果：15 个 JUnit，失败/错误/跳过均为 0；完整客户端 GameTest 于 14:47–14:49 成功，退出码 0，临时独服保存并干净停止。复核未再发现 P0/P1；理论剩余项仅是第三方更外层 Mixin 完全短路本 wrapper 的一般组合风险。

### 2026-07-18 14:51 +08:00 — 最终双构建、依赖与产物扫描

- 命令：连续两次独立 `gradlew.bat clean build`；扫描生产源码/资源/构建脚本、`runtimeClasspath`、`clientRuntimeClasspath`、最终 JAR 路径与解压内容；检查 JAR 元数据和 `git diff --check`。
- 结果：两次构建均成功，10/10 任务实际执行，分别约 27 秒和 24 秒；仅有 Gradle 10 未来兼容、Java 25 native-access/Unsafe 警告。三层禁用框架/桥接/旧配置库扫描匹配数均为 0；运行 classpath 无 Mod Menu；JAR 共 50 个条目、无嵌套 JAR、无 test/gametest 内容。`git diff --check` 仅报告 Windows 行尾转换提示，无空白错误。
- 产物：`build/libs/diggusmaximus-1.5.9+26.2.jar`，96,104 字节，SHA-256 `43ED1DC0D60151D318AD76AE9F84D801AEBCB0476B4D018DEC4D791A92EE401B`。解压元数据精确约束 Minecraft `=26.2`、Loader `>=0.19.3`、Fabric API `>=0.153.0+26.2`、Java `>=25`。

### 2026-07-18 14:52–15:45 +08:00 — 最终目标世界隔离验证夹具诊断与完成

- 安全：每次尝试都从目标实例建立独立完整副本，只启动并停止本次脚本记录的 PID；原实例未启动、未写入。原 `world\level.dat` 基线 SHA-256 为 `0E6514A6BE77A36B6706AB82EA30A07AB6B9A146D4A19A4FA55C8123C5717CED`。
- 已证实：当前最终 JAR 随目标整合包的 48 个模组在 Java 25、Minecraft 26.2、Loader 0.19.3 下完成服务端加载并到达 `Done`；测试平台填充、种子和相邻石头前置标记均成功，服务端 stdin 可保存/停止。
- 失败尝试：初版验证脚本先后遇到命名管道/主机时序、共享读取活动日志，以及 PCL 版本 JSON 的 Fabric 注入库无 `downloads.artifact` 导致验证脚本构造客户端 classpath 时漏掉 Loader、客户端报 `ClassNotFoundException: KnotClient`。这些均发生在真实玩家功能动作前，是隔离验证夹具问题，不是模组加载或功能失败；脚本已分别改为共享读取并按 Maven 坐标补全 Loader。
- 后续夹具恢复：修正 classpath 后客户端成功连接；第一次重启检查因测试代理被外部中断，第二次补充脚本又因残留 `latest.log` 把旧 `Done` 误认为新进程就绪而提前发送命令。主代理核对命令行后只终止该失败夹具专属 PID，轮换旧日志并在新 `Done` 后显式 forceload，再执行最终检查。这些失败均未改变生产代码或原目标。
- 最终结果：隔离目录 `codex-diggusmaximus-final-20260718-150235` 中，最终 JAR/48 模组服务端与完整客户端加载成功；`DiggusVerify` login/join、玩家实体和区块 marker 通过。真实窗口 Shift+左键后种子和相邻 stone 均变 air；保存/停止后重启，两个 air marker 仍保持，再次保存并干净停止。`status.json` 为 `phase=complete`，最终 JAR 哈希匹配，原目标哈希前后及收尾复核均与基线相同。本项为 `PASS`。

### 2026-07-18 15:46 +08:00 — Mod Menu 最终回归首次夹具失败（已修正）

- 命令：通过临时 init script 把 Mod Menu 20.0.1 复制到 Client GameTest 的 `mods` 目录后执行完整 `runClientGameTest`。
- 结果：Minecraft 26.2、Loader 0.19.3、Fabric API 0.153.0、Java 25、Diggus Maximus 和 Mod Menu 20.0.1 均完成加载；新增入口断言在测试线程直接构造 `DiggusConfigScreen`，被 Fabric Client GameTest 的线程保护以 `Minecraft.getInstance() cannot be called from the gametest thread` 拒绝，退出码 1。失败发生在生产入口实际判定前，不是 Mod Menu entrypoint 或生产代码崩溃。
- 修正：把 Mod Menu 工厂调用移入 `context.computeOnClient`，在渲染线程构造并断言返回同一个原生 `DiggusConfigScreen`；随后必须从头重跑完整套件，首次失败不作为通过证据。

### 2026-07-18 15:47–15:52 +08:00 — 最终双构建、Mod Menu 全量回归与审计完成

- 测试：线程修正后先执行一次带 Mod Menu 20.0.1 的完整 GameTest 并通过；为满足“最后两次构建”口径，随后连续执行两次独立 `clean build`，10/10 任务均实际执行且 15 个 JUnit 均通过；再从清理后的输出执行最终一次带 Mod Menu 的完整 GameTest。
- 结果：最终 GameTest 1 分 37 秒成功，Mod Menu 20.0.1 实际加载，entrypoint factory 返回原生 `DiggusConfigScreen`；集成服完整功能套件和真实客户端连接临时独服 3×3 连锁再次通过，两个服务器均保存并干净停止。最终日志 SHA-256 为 `24C641257F0F07E8E21CEDB24546922C0CF1AC407E4C39625DA4654B4EA37B13`。
- 扫描：生产源码/资源/构建脚本禁用框架匹配 0；两套 runtime classpath 禁用框架匹配 0、Mod Menu 匹配 0；最终 JAR 50 条目、0 嵌套 JAR、0 测试条目、路径/文本禁用框架匹配 0。产物仍为 96,104 字节，SHA-256 `43ED1DC0D60151D318AD76AE9F84D801AEBCB0476B4D018DEC4D791A92EE401B`。
- 状态：所有要求的审计、迁移、构建、清除扫描、客户端/服务器/目标世界验证均闭合；文档阶段改为 `COMPLETE`。工作树未暂存、未提交、未 push。

### 2026-07-18 16:07–16:08 +08:00 — 目标存档缺失注册项对照归因

- 原因：最终真实玩家加载目标世界区段时出现 Farmers Delight 方块注册项缺失；原目标 `latest.log` 没有玩家加载这些区段，不能单靠其零匹配证明警告由 Diggus 引入或不引入。
- 方法：在同源、已废弃的隔离副本 `codex-diggusmaximus-final-20260718-150132` 中把唯一 Diggus JAR 暂时移动到 evidence，保留世界和其余目标模组，使用独立端口启动 47 模组服务端并强制加载日志对应区块；保存、停止后在 `finally` 中恢复 JAR 和原 `server.properties`。
- 结果：不加载 Diggus 时仍复现 6 条 `farmersdelight:wild_beetroots`/`sandy_shrub` recoverable registry fallback；服务端退出码 0、全部维度保存且干净停止。证据为该副本 `evidence/server-baseline-without-diggus.latest.log`。复核 `DIGGUS_RESTORED=1`、`.disabled` 遗留 0、该副本 Java 进程 0。
- 结论：目标整包本身存在存档/模组版本不一致，不得声称 registry-clean；对照支持判断该问题可在无 Diggus 时独立复现，故不撤销 Diggus 迁移 `COMPLETE`。用户随后明确将其排除在本模组维护范围外。

### 2026-07-18 16:17 +08:00 — 外部存档问题范围确认

- 用户明确指示：“不用管他们，你维护你的 mod 就行了。”因此 Farmers Delight 缺失注册项不再作为 Diggus 迁移阻塞项。
- 未向项目加入 Farmer’s Delight 依赖，未向目标实例安装任何 JAR，也未修改原目标世界；本项目仍只维护 Diggus Maximus 的 Fabric 26.2 迁移与兼容性。
- 文档保持 `COMPLETE`；最终产物、测试、三层清除扫描和 Diggus 功能/配置/存档非侵入性结论不变。

### 2026-07-18 16:23 +08:00 — beta 打包与 GitHub Actions 发布准备

- 用户授权暂存、提交，并要求通过 GitHub Actions 在远端发布 beta。版本改为 SemVer/Fabric 兼容的 `1.5.9-beta.1+26.2`，计划标签 `v1.5.9-beta.1+26.2`。
- 仓库原先没有 `.github/workflows` 或标签；新增 tag-push 发布工作流，使用 GitHub API 核实的官方当前 release：`actions/checkout@v7.0.0`、`actions/setup-java@v5.6.0`、`gradle/actions/setup-gradle@v6.2.0`。工作流声明 `contents: write`，在 Ubuntu/Java 25 上重新 `clean build`，校验 tag 与 `mod_version` 完全一致，再用 `gh release create --prerelease --verify-tag` 上传唯一 remap JAR。
- 远端为 `https://github.com/CasseShimada/DiggusMaximus`，当前账号权限 `ADMIN`，Actions 已启用、允许全部 Actions；核心/客户端运行依赖未因发布工作流变化。
- 阶段暂改为 `VERIFICATION`。下一步：对 beta 元数据执行本地双构建、JAR/依赖扫描和带 Mod Menu 的最终完整 GameTest；通过后才提交并触发远端发布。

### 2026-07-18 16:26–16:30 +08:00 — beta 本地发布验收通过

- 命令：连续两次 `gradlew.bat clean build --no-daemon --console=plain`；注入 Mod Menu 20.0.1 后执行完整 `runClientGameTest`；解析 JUnit XML、最终 JAR/fabric 元数据、生产/工作流扫描和两套 runtime dependency report；逐条比较目标世界已验证 JAR与 beta JAR 的非版本元数据内容。
- 结果：两次构建均成功且 10/10 任务实际执行，15 个 JUnit 的失败/错误/跳过均为 0；beta GameTest 1 分 32 秒成功，实际加载 `1.5.9-beta.1+26.2` 和 Mod Menu 20.0.1，集成服、临时独服及完整功能/安全套件通过。
- 产物：`build/libs/diggusmaximus-1.5.9-beta.1+26.2.jar`，96,109 字节，SHA-256 `1F4F78A2F446058358627358A702F4A9BAB6435638708A0086FBF842D103CB6E`；50 条目、无嵌套/测试/禁用框架内容。最终日志 SHA-256 `8E9D6BEA3C6D405E163549265203646FE8C81373822D0B6F701D2A1315232429`。
- 等价性：与目标世界真实玩家验证使用的 `1.5.9+26.2` JAR相比，排除 `fabric.mod.json` 后其余 49 个条目逐项内容哈希完全一致；因此 beta 只改变发布版本元数据，不改变已在目标世界验证的生产字节码/资源。
- 下一步：暂存并提交全部迁移和工作流，push 分支与 beta 标签，等待远端 Action 重新构建并核验 prerelease/资产哈希。

### 2026-07-18 16:31–16:37 +08:00 — beta 提交、远端构建与 prerelease 发布完成

- Git：49 个迁移/测试/审计/工作流文件已提交为 `c283f139a7d9e12eb65d233fbaee42800b0298d9`（`Migrate Diggus Maximus to Minecraft 26.2 beta`）并推送到 `origin/26.2`；annotated tag `v1.5.9-beta.1+26.2` 已推送且精确指向该提交。
- Action：标签 push 触发 `Release beta` run `29637631608`；唯一 job `build-and-release` 的 checkout、Java 25、Gradle、版本/标签一致性检查、clean build、JAR 定位和 prerelease 创建步骤全部成功，run 结论为 `success`。
- Release：`Diggus Maximus 1.5.9-beta.1+26.2` 已于 16:34 +08:00 发布；`isDraft=false`、`isPrerelease=true`，唯一资产为 `diggusmaximus-1.5.9-beta.1+26.2.jar`，94,688 字节，GitHub/下载复算 SHA-256 均为 `02095D59F7A5863F534AF5704114A6BBBD23550DCD40345009CA691A5C286513`。
- 远端产物复核：JAR 共 50 个 ZIP 条目（38 个文件、12 个目录）；`fabric.mod.json` 版本为 `1.5.9-beta.1+26.2`，精确依赖 Minecraft `=26.2`、Loader `>=0.19.3`、Fabric API `>=0.153.0+26.2`、Java `>=25`，Mod Menu 仅为 `suggests >=20.0.1`。远端与本地 JAR 的全部 class 文件内容相同；五个文本资源差异仅为 Windows CRLF 与 Ubuntu LF，manifest 差异仅为相同 client-only 条目集合的排列/折行顺序，因此无生产逻辑差异。
- 链接：Action `https://github.com/CasseShimada/DiggusMaximus/actions/runs/29637631608`；prerelease `https://github.com/CasseShimada/DiggusMaximus/releases/tag/v1.5.9-beta.1%2B26.2`。
- 状态：用户要求的审计、迁移、验证、暂存、提交、push 和通过 GitHub Actions 远端发布 beta 全部闭合，阶段恢复为 `COMPLETE`。
