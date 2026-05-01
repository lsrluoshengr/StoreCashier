# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
StoreCashier is an Android mobile cash register app for small convenience stores. It uses the phone camera to scan barcodes for product entry, inventory management, and checkout settlement.

## Build & Run
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.storecashier.ExampleUnitTest"

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Tech Stack
- **Language**: Java 19 (sourceCompatibility/targetCompatibility)
- **Build**: Gradle 8.13, Android Gradle Plugin 8.7.0
- **SDK**: minSdk 24, targetSdk 36, compileSdk 36
- **Database**: Room 2.6.1 (replaces raw SQLite)
- **Architecture**: ViewModel + LiveData (lifecycle 2.6.2)
- **Barcode scanning**: ZXing (android-embedded 4.3.0, core 3.5.1)
- **JSON**: Gson 2.10.1
- **Cloud sync**: WebDAV via sardine-android 0.9 + OkHttp 4.12.0
- **Repositories**: Aliyun mirrors configured in settings.gradle for faster builds in China

## Architecture

MVVM pattern with single Activity + bottom navigation. `MainActivity` hosts four fragments shown/hidden on tab switch:

| Tab | Fragment | Purpose |
|-----|----------|---------|
| Settlement | `SettlementFragment` | Scan products, build cart, checkout |
| Add Product | `AddProductFragment` | Scan barcode, enter product details |
| Inventory | `InventoryFragment` | View/edit/delete all products (LiveData-observed) |
| Settings | `SettingsFragment` | Backup/restore via WebDAV or local JSON export |

### Data Layer
- `Product.java` — Room `@Entity` with `Parcelable`, barcode has a unique index
- `ProductDao.java` — Room `@Dao`: CRUD, `insertAll` (REPLACE strategy for import), LiveData queries, sync queries for background work
- `AppDatabase.java` — singleton Room database (`StoreCashierDB`), exposes a 4-thread `databaseWriteExecutor` for all background DB operations
- `ProductViewModel.java` — `AndroidViewModel` wrapping ProductDao, exposes `LiveData<List<Product>>` for reactive UI updates

### Data Flow
- All fragments share the same `ProductViewModel` instance via `ViewModelProvider(requireActivity())`
- Write operations go through `AppDatabase.databaseWriteExecutor` to run off the main thread
- `InventoryFragment` observes `LiveData<List<Product>>` for automatic UI刷新
- `SettlementFragment` queries products on background thread, posts UI updates via `runOnUiThread`
- Barcode is the natural key for products (unique index). Settlement decrements stock by quantity.

### Backup & Sync
- `WebDAVManager.java` — backup/restore product data to a WebDAV server (e.g. 坚果云). Uses fake Chrome User-Agent to avoid 403.
- Local export: `Downloads/Cashier/Backup_<timestamp>.json` via Gson
- Local import: system file picker (`ACTION_GET_CONTENT`) for JSON files
- WebDAV restore: lists cloud `.json` files, user selects which to restore

## Key Technical Details
- ZXing's `CaptureActivity` is declared in AndroidManifest with fullscreen portrait theme override
- `SettlementFragment` uses continuous scan mode (`decodeContinuous`) with 1.5s debounce
- `AddProductFragment` uses single-scan mode via ZXing `CaptureActivity` Intent
- Gson uses lenient mode for import to handle format variations
- Database name: `StoreCashierDB`, single table: `product`
