# StoreCashier - 便利店迷你收银台

> 一部手机，就是一个移动收银台。

## 背景

我家楼下开了间 15㎡ 的便民便利店，主营零食饮料、日用百货。起初觉得店小、人流少，"手算+脑子记价"完全能应付，可随着商品越摆越多，问题渐渐暴露：

- 货架上 100+ 种商品，价格记混是常事——把 3.5 元的矿泉水错卖成 2.5 元，亏了钱都没察觉
- 店铺空间有限，连迷你收银机都摆不下，结算全靠手机计算器
- 库存管理一团乱，经常等商品卖完才发现断货了

直到某天扫完一瓶饮料后大脑突然"空白"，完全想不起价格。那份尴尬让我下定决心：必须找到一个**低成本、省空间**的收银解决方案。

盯着手里的手机摄像头，一个念头冒出来——为什么不用手机开发一款简易收银 APP？全程用手机摄像头扫码操作，不用额外购买扫码枪，保证小店日常使用足够简单、顺手。

---

## 功能特性

### 扫码录入商品
- 打开 APP，摄像头对准商品条形码，扫描即可触发录入流程
- 手动补充商品名、售价、初始库存、分类，确认后保存
- 支持选择商品图片（从相册选取），图片安全存储到应用私有目录
- 后续同款商品扫码即可识别，不用靠脑子记价格

### 商品分类管理
- 录入商品时可指定分类（如"饮料"、"零食"、"日用品"等）
- 分类输入框支持自动补全，已有分类一键选取
- 库存管理页直接显示商品分类标签

### 实时库存管理
- 商品录入后自动生成库存记录
- 结算时每扫一次商品，后台库存自动减 1
- 打开 APP 直观查看哪些商品快卖完
- 支持点击商品弹出编辑对话框，可修改名称、价格、库存、分类和图片

### 扫码结算与购物车
- 顾客选完商品后，拿起手机对着条形码逐个扫描
- 屏幕实时显示当前商品图片、名称 + 单价，自动累加计算总价
- 购物车支持 **+** / **−** 按钮快速增减数量，也可点击数量手动输入
- 数量减至 0 时弹窗确认是否移除该商品
- 确认结算后库存原子扣减，订单自动归档

### 历史订单
- 结算完成后自动生成订单记录，包含订单时间和商品明细
- 设置页可查看全部历史订单，点击展开查看具体商品列表
- 订单数据持久化存储，支持后续查询

### 数据备份与恢复
- **本地导出/导入**：将商品数据导出为 JSON 文件保存到手机存储，支持从文件管理器导入恢复
- **WebDAV 云端备份**：支持坚果云等 WebDAV 服务，一键备份到云端，可选择指定备份文件恢复

---

## 技术架构

### 整体架构

MVVM 架构，单 Activity + 多 Fragment，底部导航切换。

```
┌─────────────────────────────────────────────┐
│                 MainActivity                 │
│  ┌─────────┬──────────┬──────────┬────────┐ │
│  │ 结算    │ 录入商品  │ 库存管理 │ 设置   │ │
│  │Fragment │ Fragment │ Fragment │Fragment│ │
│  └────┬────┴────┬─────┴────┬─────┴────┬───┘ │
│       │         │          │          │      │
│       │    (管理Fragment)   │     ┌────▼─────┐
│       │         │          │     │ 订单历史  │
│       │         │          │     │ Fragment  │
│       │         │          │     └────┬─────┘ │
│  ┌────▼─────────▼──────────▼──────────▼───┐ │
│  │          ProductViewModel              │ │
│  │   (LiveData<Product/Order/OrderItem>)  │ │
│  └────────────────┬───────────────────────┘ │
│                   │                          │
│  ┌────────────────▼───────────────────────┐ │
│  │      AppDatabase (Room / SQLite)       │ │
│  │  Product | Order | OrderItem (v4)      │ │
│  │    databaseWriteExecutor (4 threads)   │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 19 |
| 构建 | Gradle + AGP | 8.13 / 8.7.0 |
| SDK | minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| 数据库 | Room | 2.6.1 |
| 架构组件 | ViewModel + LiveData | 2.6.2 |
| 条码扫描 | ZXing (android-embedded + core) | 4.3.0 / 3.5.1 |
| JSON | Gson | 2.10.1 |
| 云同步 | WebDAV (sardine-android + OkHttp) | 0.9 / 4.12.0 |
| 图片加载 | Glide | 4.16.0 |

### 数据模型

`Product` 实体（Room `@Entity`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 自增主键 |
| barcode | String | 条形码（唯一索引） |
| name | String | 商品名称 |
| price | double | 单价 |
| stock | int | 库存数量 |
| category | String | 商品分类 |
| imagePath | String | 商品图片本地路径 |

`Order` 实体（Room `@Entity`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 自增主键 |
| orderTime | long | 下单时间戳 |

`OrderItem` 实体（Room `@Entity`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 自增主键 |
| orderId | long | 所属订单 ID |
| productId | int | 关联商品 ID |
| productName | String | 商品名称（快照） |
| price | double | 下单时单价（快照） |
| quantity | int | 购买数量 |

### 项目结构

```
app/src/main/java/com/example/storecashier/
├── MainActivity.java           # 主界面，底部导航 + Fragment 容器
├── SettlementFragment.java     # 结算：连续扫码 → 购物车（+/-按钮）→ 扣库存
├── AddProductFragment.java     # 录入：单次扫码 → 填写信息（含分类/图片）→ 入库
├── InventoryFragment.java      # 库存：LiveData 观察，编辑对话框（含图片更换）
├── SettingsFragment.java       # 设置：WebDAV 配置、本地导入导出、历史订单入口、关于页
├── OrderHistoryFragment.java   # 历史订单：查看所有订单记录及明细
├── ProductViewModel.java       # ViewModel，暴露 LiveData、写操作入口和原子结算事务
├── AppDatabase.java            # Room 单例数据库 + 线程池 (v4，含迁移链)
├── ProductDao.java             # Room DAO，LiveData 查询 + 同步查询 + 分类查询
├── OrderDao.java               # Room DAO，订单和订单明细查询
├── Product.java                # Room Entity + Parcelable（含 category/imagePath）
├── Order.java                  # Room Entity，订单主表
├── OrderItem.java              # Room Entity，订单明细表
├── CartItem.java               # 购物车条目模型（商品 + 数量）
├── CartItemAdapter.java        # 购物车 RecyclerView 适配器（+/-按钮、图片、手动输入）
├── OrderAdapter.java           # 订单列表 RecyclerView 适配器（点击展开明细）
├── FileUtil.java               # 工具类，将图片拷贝到应用私有目录
├── WebDAVManager.java          # WebDAV 备份/恢复/连接测试
└── AboutActivity.java          # 关于页面
```

---

## 构建与运行

### 环境要求
- Android Studio (推荐 Hedgehog 或更新版本)
- JDK 19
- Android SDK 36

### 构建命令

```bash
# 调试版 APK
./gradlew assembleDebug

# 发布版 APK
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.example.storecashier.ExampleUnitTest"

# 仪器化测试（需连接设备或模拟器）
./gradlew connectedAndroidTest
```

### 安装到手机

```bash
# 构建并安装调试版
./gradlew installDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/` 目录。

---

## 使用说明

1. **首次使用**：进入「录入商品」页签，扫描商品条形码，填写名称、价格、库存、分类，可选择一张商品图片后保存
2. **日常收银**：进入「结算」页签，点击「开启扫码」，对准商品条形码逐个扫描，购物车中可通过 +/− 按钮调整数量，确认总价后点击「确认结算」
3. **库存查看**：进入「库存管理」页签，查看所有商品库存及分类；点击商品可编辑信息（含更换图片）
4. **历史订单**：进入「设置」页签，点击「查看历史订单」可浏览所有结算记录，点击订单可展开查看商品明细
5. **数据备份**：进入「设置」页签，可导出本地 JSON 文件，或配置 WebDAV 服务进行云端备份

---

## 许可证

本项目仅供个人学习和小型店铺自用。
