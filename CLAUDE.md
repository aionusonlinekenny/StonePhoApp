# StonePhoApp — CLAUDE.md

## Tổng quan dự án
Android POS app cho nhà hàng **Stone Pho** (1525 Baytree Rd, Ste M, Valdosta, GA).  
Stack: Kotlin + Jetpack Compose · Firebase · Clover REST API v3 · ESC/POS thermal printer.

---

## Cấu hình & Secrets

| Key | Giá trị |
|-----|---------|
| Proxy API Key | `StonePhoClover@2024` |
| Clover Merchant ID | `GW3XFCV71AK81` |
| Clover API Token | `c30698f2-347e-add6-b758-44285d0e6cac` *(server-side PHP only — không đưa vào Android code)* |
| Clover App ID | `NJVXQ3773SZFP` |
| LoyaltApp App ID | `MGGRQ01WQYC8A` |
| Backend URL | `https://www.stonephovaldosta.com/loyalteapp/backend/stonepho_clover.php` |
| PHP version server | 7.4.33 · cURL YES |

---

## Kiến trúc Clover Dining

### Luồng dữ liệu
```
Android app
  → stonepho_clover.php (PHP proxy, xác thực bằng Bearer token)
    → Clover REST API v3 (atomic_order/orders + expand=payments)
```

### Giới hạn quan trọng của Clover API (đã xác nhận)
- `state` và `paymentState` **KHÔNG BAO GIỜ được cập nhật** khi thanh toán bằng thẻ qua Clover Dining POS.
- **Giải pháp**: expand thêm `payments` trong API call. Nếu `payments.elements` có dữ liệu → order đã được thanh toán bằng thẻ → tự đóng bàn trong app.
- `atomic_order/orders` trả về tất cả Dining orders kể cả đã đóng (không phân biệt qua state).

### Logic đóng bàn
| Trường hợp | Cách xử lý |
|---|---|
| Khách trả **cash** | Staff bấm 💵 Cash → lưu Invoice + in ESC/POS receipt → đóng bàn qua SharedPreferences |
| Khách trả **thẻ** qua Clover POS | Clover ghi payment record → `payments.elements` non-empty → `applyFilter` tự loại bỏ → bàn chuyển trắng |
| Bàn **mới ngồi lại** sau khi đóng | `order.createdTime > closedTime` → tự động mở lại |

### `applyFilter` — các điều kiện loại order
```kotlin
!hasPaid          // payments.elements không rỗng → đã trả thẻ qua POS
&& !stateIsClosed // state == "deleted" | "closed"
&& createdTime >= 8h ago
&& (closeTime == null || createdTime > closeTime)  // chưa đóng cash, hoặc đơn mới hơn
```

---

## Màu sắc bàn (floor plan)

| Trạng thái | Nền | Viền |
|---|---|---|
| Trống (không có order) | Trắng | Xanh đậm 1.5dp |
| Có khách (đang mở) | Xanh đậm `#1565C0` | — |
| Đang chọn (selected) | Xanh đậm hơn `#0D47A1` | Xanh nhạt 2.5dp |

---

## Model quan trọng

### `Invoice` (`OrderInvoiceModel.kt`)
```kotlin
data class Invoice(
    val id: String,
    val items: List<Product>,
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val total: Double,
    val date: String,
    val time: String,
    val tableTitle: String? = null   // PHẢI là nullable — Gson không áp default value
)
```
**Quy tắc**: Gson khi deserialize JSON cũ không có `tableTitle` sẽ set = `null` dù Kotlin default là `""`. Nếu dùng `String = ""` sẽ crash `NullPointerException` khi gọi `.isNotBlank()`. Luôn dùng `String? = null` và kiểm tra bằng `.isNullOrBlank()`.

### `CloverOrder` (`CloverModels.kt`)
- Dùng `title` (không phải `tableLabel`) để lấy tên/số bàn.
- `payments: CloverPaymentsWrapper?` — field quyết định bàn đã trả thẻ hay chưa.

---

## SharedPreferences — Local Closed Tables
- Prefs name: `clover_local`, key: `closed_tables`
- Format: `Map<String, Long>` (tableName → closeTimestamp ms)
- Tự purge entries cũ hơn 16 giờ khi load
- Dùng để track bàn đã đóng bằng cash (app-side), không liên quan đến Clover API

---

## PHP Backend (`stonepho_clover.php`)

### Quy tắc
- Xác thực bằng `Bearer` header hoặc `?key=` param, so với `API_KEY`.
- Dùng `die()` thay vì `echo` + `break` để tránh output thừa gây HTTP 500.
- Expand orders luôn bao gồm: `lineItems,lineItems.item,orderType,payments`
- PHP 7.4 compatible — không dùng named arguments hay syntax PHP 8+.
- Giữ `define('SCRIPT_VER', ...)` để verify phiên bản file đã upload đúng chưa.

### Khi debug HTTP 500
1. Tạo `stonepho_test.php` → kiểm tra PHP version, cURL
2. Tạo `stonepho_debug.php` → chạy từng bước
3. Thêm `ini_set('display_errors', 1)` tạm thời
4. Thường nguyên nhân: file cũ vẫn còn trên server, chưa upload file mới

---

## Quy tắc phát triển chung

### Sau mỗi lần sửa chữa, PHẢI làm:
1. Đọc lại `CLAUDE.md` để không vi phạm các quy tắc đã ghi
2. Cập nhật `CLAUDE.md` nếu có phát hiện mới, thay đổi logic, hoặc fix bug quan trọng
3. Commit tất cả file liên quan (kể cả PHP backend nếu có thay đổi)
4. Push lên branch `claude/fix-printer-discovery-im6H3`

### Kotlin / Compose
- Không dùng `isNotBlank()` trực tiếp trên field có thể null — dùng `isNullOrBlank()`
- Gson model fields nên có default value (`= ""`, `= 0`, `= null`) để không crash khi JSON thiếu field
- `derivedStateOf` cho computed state phụ thuộc vào state khác
- Silent refresh: chỉ dùng `isFirstLoad` cho loading overlay; `isRefreshing` chỉ cho icon; status bar luôn visible

### Android
- SharedPreferences: dùng `apply()` thay `commit()` (async, không block UI)
- Gson TypeToken cho generic types: `object : TypeToken<Map<String, Long>>() {}.type`
- `PrinterConfig.getSelectedIpPort()` có thể null — luôn có fallback IP

### Clover API
- Luôn expand `payments` trong order query để detect card payment
- Dùng `title` của order làm table identifier (không phải `tableLabel`, không phải `id`)
- Không tin vào `state`/`paymentState` — chúng không cập nhật sau Dining payment
- Thời gian lọc: 8 giờ gần nhất (tránh lấy order ngày hôm qua theo UTC)

---

## Màn hình chính — thứ tự nút
```
[🖥️ Clover Dining]  [💳 Quick Pay]  [📋 Invoice Check]  [⚙️ Settings]
```

## Refresh interval
- Clover Dining floor plan: **5 giây** (silent background, không flicker)

---

## Bẫy Kotlin quan trọng — `isSelected` với nullable

```kotlin
// BUG: null?.id == null?.id → null == null → TRUE
// → mọi bàn trống đều bị mark isSelected = true → render màu xanh đậm (selected)
isSelected = selectedOrder?.id == order?.id

// FIX: bàn trống (order == null) không bao giờ được là "selected"
isSelected = order != null && selectedOrder?.id == order.id
```

**Triệu chứng**: tất cả bàn trống đều xanh, không có cách nào force trắng được dù đã thử `Card`, `Surface`, `Box+background`, `drawBehind` — vì màu white không bao giờ được render do điều kiện `isSelected` đứng trước.

---

## TableSlot — tách display label và Clover lookup key

```kotlin
private data class TableSlot(
    val row: Int,
    val col: Int,
    val name: String,               // hiển thị trên cell
    val seats: Int = 4,
    val cloverTitle: String = name  // khớp với order.title từ Clover API
)
```

**Quy tắc**: Dùng `cloverTitle` để lookup `tableOrderMap`, dùng `name` để hiển thị. Khi tên bàn trong app khác với title trên Clover (ví dụ `"OUTS"` vs `"OUTSIDE"`), khai báo `cloverTitle` riêng:

```kotlin
TableSlot(1, 5, "OUTS", cloverTitle = "OUTSIDE")
```

---

## Tính năng Cash Payment — Change Calculator

Khi staff bấm **💵 Cash**, flow là:
1. Popup change calculator: hiển thị tổng, nhập tiền nhận, tính tiền thối realtime
2. Nút "Xác nhận & In" chỉ active khi tiền nhận ≥ tổng
3. In receipt (có dòng Cash received / Change)
4. Gọi `DELETE /orders/{id}` qua PHP proxy → bàn release trên Clover POS ngay
5. Dialog kết quả → "OK · Đóng bàn"

---

## Sync Menu từ Clover POS (`ManageMenuScreen.kt`)

### Luồng
```
ManageMenuScreen → fetchSyncData()
  → CloverRepository.fetchCatalogCategoriesViaProxy()  (?action=categories)
  → CloverRepository.fetchCatalogItemsViaProxy()       (?action=items)
  → showSyncPreview dialog
      → "Hợp nhất" → mergeFromClover()  → OrderViewModel.replaceMenu()
      → "Thay toàn bộ" → replaceWithClover() → OrderViewModel.replaceMenu()
```

### Helper functions (top-level private trong ManageMenuScreen.kt)
- `mergeFromClover(cloverCats, cloverItems, currentCats, currentProds)`: Chỉ thêm categories/products chưa có (so sánh lowercase name). Giữ nguyên màu và thứ tự hiện có.
- `replaceWithClover(cloverCats, cloverItems, currentCats)`: Xóa toàn bộ, tạo lại từ Clover. Giữ `colorHex` nếu category name đã tồn tại; category mới dùng màu từ `SYNC_COLORS` (vòng lặp).

### PHP endpoints mới
- `?action=categories` → `/v3/merchants/{id}/categories?limit=200`
- `?action=items` → `/v3/merchants/{id}/items?expand=categories&limit=1000` (lọc bỏ hidden và không có category)

### Lưu ý
- Sau khi sync, `OrderViewModel.replaceMenu()` tự gọi `saveMenu()` → lưu vào `catalog_data.json` + `product_data.json`
- **Cần upload PHP file lên server** sau mỗi lần sửa `stonepho_clover.php`

---

## Tính năng Split Bill theo item (`CloverOrderScreen.kt`)

Khi staff bấm **✂️ Chia bill**, flow là:
1. `SplitBillDialog` mở — hiển thị tất cả items còn lại (remainingItems)
2. Staff tap item để toggle chọn/bỏ chọn (highlight tím khi chọn)
3. Bấm "🖨️ In phần N & tiếp" → in receipt cho người đó → items bị xóa khỏi pool
4. Lặp lại cho đến khi `remainingItems.isEmpty()` → hiển thị "✅ Đã tính hết"
5. "✔ Đóng bàn" → lưu Invoice + xóa order Clover + đóng dialog

**Không có giới hạn số phần** — chia bao nhiêu lần tùy ý.

---

## Lịch sử fix quan trọng

| Ngày | Vấn đề | Giải pháp |
|------|---------|-----------|
| 2025-07 | App crash khi vào Invoice Check | `tableTitle: String = ""` → `String? = null`; thay `.isNotBlank()` bằng `.isNullOrBlank()` |
| 2025-07 | HTTP 500 từ PHP proxy | File cũ chưa được upload; thêm version marker để xác nhận |
| 2025-07 | Quá nhiều bàn hiện là "có khách" dù POS đã đóng | Thêm `payments` expand; filter `hasPaid = payments.elements.isNotEmpty()` |
| 2025-07 | Bàn trống màu xám không giống POS | Đổi empty table = nền trắng + viền xanh đậm |
| 2025-07 | Màn hình giật khi refresh | Tách `isFirstLoad` vs `isRefreshing`; status bar không bao giờ ẩn |
| 2026-07 | Bàn trống vẫn xanh dù đã force Color.White nhiều cách | Bẫy `null == null → true` trong `isSelected`; fix bằng `order != null && selectedOrder?.id == order.id` |
| 2026-07 | Bàn OUTS không nhận data dù Clover gửi đúng | Tên slot `"OUTS"` không khớp Clover title `"OUTSIDE"`; dùng `cloverTitle = "OUTSIDE"` |
| 2026-07 | Cash payment không release bàn trên Clover POS | Thêm `action=delete_order` vào PHP proxy; gọi sau khi in receipt |
| 2026-08 | Button3D không có param `enabled` | Dùng `canPrint` guard trong `onClick` + đổi gradient sang xám khi inactive |
| 2026-08 | Sync menu từ Clover POS | PHP thêm `action=categories/items`; Android thêm CloverRepository + ManageMenuScreen Sync POS button |
