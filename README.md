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
- 手动补充商品名、售价、初始库存，确认后保存
- 后续同款商品扫码即可识别，不用靠脑子记价格

### 实时库存管理
- 商品录入后自动生成库存记录
- 结算时每扫一次商品，后台库存自动减 1
- 打开 APP 直观查看哪些商品快卖完

### 扫码结算
- 顾客选完商品后，拿起手机对着条形码挨个扫描
- 屏幕实时显示当前商品名称 + 单价，自动累加计算总价
- 确认结算后库存自动扣减

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
│  ┌────▼─────────▼──────────▼──────────▼───┐ │
│  │          ProductViewModel              │ │
│  │        (LiveData<List<Product>>)       │ │
│  └────────────────┬───────────────────────┘ │
│                   │                          │
│  ┌────────────────▼───────────────────────┐ │
│  │      AppDatabase (Room / SQLite)       │ │
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

### 数据模型

`Product` 实体（Room `@Entity`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 自增主键 |
| barcode | String | 条形码（唯一索引） |
| name | String | 商品名称 |
| price | double | 单价 |
| stock | int | 库存数量 |

### 项目结构

```
app/src/main/java/com/example/storecashier/
├── MainActivity.java           # 主界面，底部导航 + Fragment 容器
├── SettlementFragment.java     # 结算：连续扫码 → 购物车 → 扣库存
├── AddProductFragment.java     # 录入：单次扫码 → 填写信息 → 入库
├── InventoryFragment.java      # 库存：LiveData 观察，管理模式批量删除
├── SettingsFragment.java       # 设置：WebDAV 配置、本地导入导出、关于页
├── ProductViewModel.java       # ViewModel，暴露 LiveData 和写操作入口
├── AppDatabase.java            # Room 单例数据库 + 线程池
├── ProductDao.java             # Room DAO，LiveData 查询 + 同步查询
├── Product.java                # Room Entity + Parcelable
├── CartItem.java               # 购物车条目模型（商品 + 数量）
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

1. **首次使用**：进入「录入商品」页签，扫描商品条形码，填写名称、价格、库存后保存
2. **日常收银**：进入「结算」页签，点击「开启扫码」，对准商品条形码逐个扫描，确认总价后点击「确认结算」
3. **库存查看**：进入「库存管理」页签，查看所有商品库存；长按商品可编辑信息；点击「管理」可批量删除
4. **数据备份**：进入「设置」页签，可导出本地 JSON 文件，或配置 WebDAV 服务进行云端备份

---

## 许可证

本项目仅供个人学习和小型店铺自用。
