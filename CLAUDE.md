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
- **Database**: Room 2.6.1 with manual migrations (currently version 4)
- **Architecture**: ViewModel + LiveData (lifecycle 2.6.2)
- **Barcode scanning**: ZXing (android-embedded 4.3.0, core 3.5.1)
- **Image loading**: Glide 4.16.0
- **JSON**: Gson 2.10.1
- **Cloud sync**: WebDAV via sardine-android 0.9 + OkHttp 4.12.0
- **Repositories**: Aliyun mirrors configured in settings.gradle for faster builds in China

## Architecture

MVVM pattern with single Activity + bottom navigation. `MainActivity` hosts fragments shown/hidden on tab switch:

| Tab | Fragment | Purpose |
|-----|----------|---------|
| Settlement | `SettlementFragment` | Scan products, build cart, checkout, create orders |
| Add Product | `AddProductFragment` | Scan barcode, pick image, enter product details |
| Inventory | `InventoryFragment` | View/edit/delete products (LiveData-observed), manage mode for batch delete |
| Settings | `SettingsFragment` | WebDAV config/backup/restore, local export/import, navigate to order history |

`OrderHistoryFragment` is pushed onto the back stack from Settings, not a bottom nav tab.

### Data Layer (Room, DB version 4)

**Entities**:
- `Product` — `@Entity`, barcode unique index, fields: id, barcode, name, price, stock, category, imagePath. Implements `Parcelable`.
- `Order` — `@Entity` (`orders`), fields: orderId (auto), timestamp, totalAmount
- `OrderItem` — `@Entity` (`order_items`), FK to Order with CASCADE delete. Fields: orderItemId, orderId, barcode, productName, price, quantity

**DAOs**:
- `ProductDao` — CRUD, `insertAll` (REPLACE for import), LiveData queries, sync queries for background work
- `OrderDao` — insert order + items, LiveData list of all orders, sync query for order items by orderId

**Database**: `AppDatabase.java` — singleton (`StoreCashierDB`), 4-thread `databaseWriteExecutor`, migrations 1→2 (category), 2→3 (Order/OrderItem tables), 3→4 (imagePath)

**ViewModel**: `ProductViewModel` — wraps ProductDao, exposes `LiveData<List<Product>>` and write methods

### Data Flow
- All fragments share the same `ProductViewModel` via `ViewModelProvider(requireActivity())`
- Write operations go through `AppDatabase.databaseWriteExecutor` off the main thread
- `InventoryFragment` observes `LiveData<List<Product>>` for auto-refresh
- `SettlementFragment` queries products on background thread, posts UI via `runOnUiThread`
- Settlement creates an `Order` + `OrderItem` list in the database, then decrements product stock
- Barcode is the natural key for products (unique index)

### Image Handling
- Product images are picked from gallery via `ACTION_GET_CONTENT` with `"image/*"`
- `FileUtil.copyImageToInternal()` copies picked images to `context.getFilesDir()/images/` as `img_<barcode>_<timestamp>.jpg`
- Images are displayed via Glide loading from `File(imagePath)` with `default_img` placeholder
- `imagePath` is an absolute path stored in the product table; images in internal storage are deleted on app uninstall

### Backup & Sync
- `WebDAVManager.java` — backup/restore to WebDAV server (e.g. 坚果云). Fake Chrome User-Agent to avoid 403.
- **WebDAV backup** creates a folder `Backup_<timestamp>/` containing `products.json` + `images/` with all product image files
- **WebDAV restore** downloads the folder, restores images to local `getFilesDir()/images/`, updates `imagePath` in each product, then inserts via `productDao.insertAll()`
- **Local export** to `Downloads/Cashier/Backup_<timestamp>/products.json` + `images/`
- **Local import** via system file picker (`ACTION_GET_CONTENT`) for JSON files (images not included in local import)

## Key Technical Details
- ZXing `CaptureActivity` declared in AndroidManifest with fullscreen portrait theme override
- `SettlementFragment` uses continuous scan mode (`decodeContinuous`) with 1.5s debounce
- `AddProductFragment` uses single-scan mode via ZXing `CaptureActivity` Intent
- Glide loads product images from `File` objects (not URLs), with `R.drawable.default_img` as placeholder
- `SettlementFragment` and `InventoryFragment` both have `imagePickerLauncher` for picking product images
- Old image files are never cleaned up when a product image is replaced
