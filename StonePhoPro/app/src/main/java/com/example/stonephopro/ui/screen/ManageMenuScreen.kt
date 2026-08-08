package com.example.stonephopro.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.*
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.ExtraStorage
import com.example.stonephopro.utils.clover.CloverCatalogCategory
import com.example.stonephopro.utils.clover.CloverCatalogItem
import com.example.stonephopro.utils.clover.CloverConfig
import com.example.stonephopro.utils.clover.CloverRepository
import com.example.stonephopro.viewmodel.OrderViewModel
import com.example.stonephopro.utils.PriceInputPad
import kotlinx.coroutines.launch

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

private val SYNC_COLORS = listOf("#90CAF9", "#A5D6A7", "#FFCC80", "#F48FB1", "#CE93D8", "#ECEFF1")

private fun mergeFromClover(
    cloverCats: List<CloverCatalogCategory>,
    cloverItems: List<CloverCatalogItem>,
    currentCats: List<Category>,
    currentProds: List<Product>
): Pair<List<Category>, List<Product>> {
    val existingCatNames = currentCats.associate { it.name.lowercase() to it.colorHex }
    val mergedCats = currentCats.toMutableList()
    cloverCats.filter { it.name.isNotBlank() }.forEach { cc ->
        if (!existingCatNames.containsKey(cc.name.lowercase()))
            mergedCats.add(Category(cc.name, SYNC_COLORS[mergedCats.size % SYNC_COLORS.size]))
    }
    val sortedCats = mergedCats.sortedBy { it.name }
    val existingProdNames = currentProds.map { it.name.lowercase() }.toSet()
    val mergedProds = currentProds.toMutableList()
    var nextId = (currentProds.maxOfOrNull { it.id } ?: 0) + 1
    cloverItems.forEach { ci ->
        if (!existingProdNames.contains(ci.name.lowercase())) {
            val catName = ci.categories?.elements?.firstOrNull()?.name ?: return@forEach
            mergedProds.add(Product(nextId++, ci.name, ci.price / 100.0, catName, colorHex = "#ECEFF1"))
        }
    }
    val sortedProds = mergedProds.sortedBy { it.name }
    return Pair(sortedCats, sortedProds)
}

private fun replaceWithClover(
    cloverCats: List<CloverCatalogCategory>,
    cloverItems: List<CloverCatalogItem>,
    currentCats: List<Category>
): Pair<List<Category>, List<Product>> {
    val existingColorMap = currentCats.associate { it.name.lowercase() to it.colorHex }
    val sortedCloverCats = cloverCats.sortedBy { it.name }
    val newCats = sortedCloverCats.mapIndexed { idx, cc ->
        Category(cc.name, existingColorMap[cc.name.lowercase()] ?: SYNC_COLORS[idx % SYNC_COLORS.size])
    }
    val catNameSet = newCats.map { it.name.lowercase() }.toSet()
    var nextId = 1
    val newProds = cloverItems.sortedBy { it.name }.mapNotNull { ci ->
        val catName = ci.categories?.elements?.firstOrNull()?.name ?: return@mapNotNull null
        if (!catNameSet.contains(catName.lowercase())) return@mapNotNull null
        Product(nextId++, ci.name, ci.price / 100.0, catName, colorHex = "#ECEFF1")
    }
    return Pair(newCats, newProds)
}

@Composable
fun DropdownMenuBox(
    selectedText: String,
    options: List<Pair<String, String>>,
    onColorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (color, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onColorSelected(color)
                        expanded = false
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun ManageMenuScreen(viewModel: OrderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Clover sync state ────────────────────────────────────────────────────
    var isSyncing        by remember { mutableStateOf(false) }
    var syncError        by remember { mutableStateOf("") }
    var syncPreviewCats  by remember { mutableStateOf<List<CloverCatalogCategory>>(emptyList()) }
    var syncPreviewItems by remember { mutableStateOf<List<CloverCatalogItem>>(emptyList()) }
    var showSyncPreview  by remember { mutableStateOf(false) }
    var syncDoneMsg      by remember { mutableStateOf("") }

    fun fetchSyncData() {
        isSyncing = true
        syncError = ""
        scope.launch {
            val catsResult  = CloverRepository.fetchCatalogCategoriesViaProxy(CloverConfig.PROXY_SECRET)
            val itemsResult = CloverRepository.fetchCatalogItemsViaProxy(CloverConfig.PROXY_SECRET)
            when {
                catsResult.isFailure  -> syncError = "❌ Lỗi categories: ${catsResult.exceptionOrNull()?.message}"
                itemsResult.isFailure -> syncError = "❌ Lỗi items: ${itemsResult.exceptionOrNull()?.message}"
                else -> {
                    syncPreviewCats  = catsResult.getOrDefault(emptyList())
                    syncPreviewItems = itemsResult.getOrDefault(emptyList())
                    showSyncPreview  = true
                }
            }
            isSyncing = false
        }
    }

    var newCategoryName by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(-1) }
    var selectedColor by remember { mutableStateOf("#90CAF9") }
    var selectedProductColor by remember { mutableStateOf("#ECEFF1") }
    var newProductName by remember { mutableStateOf("") }
    var productPriceCents by remember { mutableIntStateOf(0) }
    var selectedProductIndex by remember { mutableIntStateOf(-1) }
    var selectedCategoryForProduct by remember { mutableStateOf(viewModel.categories.firstOrNull()?.name ?: "") }

    var selectedExtraCategory by remember { mutableStateOf(viewModel.categories.firstOrNull()?.name ?: "") }
    var extraName by remember { mutableStateOf("") }
    var extraPriceCents by remember { mutableIntStateOf(0) }
    var selectedExtraIndex by remember { mutableIntStateOf(-1) }
    var currentExtras by remember { mutableStateOf(listOf<ExtraOption>()) }

    LaunchedEffect(selectedExtraCategory) {
        val listName = selectedExtraCategory.lowercase().replace(" ", "_") + "_extras"
        currentExtras = ExtraStorage.loadExtras(context, listName)
    }

    val colorOptions = listOf(
        "#90CAF9" to "Xanh", "#A5D6A7" to "Lá", "#FFCC80" to "Cam",
        "#F48FB1" to "Hồng", "#CE93D8" to "Tím", "#ECEFF1" to "Xám"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button3D(
                text = "⬅",
                onClick = onBack,
                modifier = Modifier.height(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
                listOf("Mục lớn", "Sản phẩm", "Extra item").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    ) {
                        Text(label, fontSize = 18.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            } else {
                Button3D(
                    text = "🔄 Sync POS",
                    onClick = { fetchSyncData() },
                    modifier = Modifier.height(48.dp),
                    fontSize = 13.sp,
                    gradientColors = listOf(Color(0xFF7B1FA2), Color(0xFF4A148C))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Tên danh mục", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(64.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎨 Màu:", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            DropdownMenuBox(
                                selectedText = colorOptions.find { it.first == selectedColor }?.second ?: "Chọn màu",
                                options = colorOptions,
                                onColorSelected = { selectedColor = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button3D(
                            text = if (selectedCategoryIndex >= 0) "💾 Cập nhật" else "➕ Thêm",
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    if (selectedCategoryIndex >= 0) {
                                        viewModel.categories[selectedCategoryIndex] = Category(newCategoryName, selectedColor)
                                        selectedCategoryIndex = -1
                                    } else {
                                        viewModel.categories.add(Category(newCategoryName, selectedColor))
                                    }
                                    newCategoryName = ""
                                    selectedColor = "#90CAF9"
                                    viewModel.saveMenu()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(viewModel.categories) { index, cat ->
                            var showConfirm by remember { mutableStateOf(false) }

                            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        if (index > 0) {
                                            viewModel.categories.swap(index, index - 1)
                                            viewModel.saveMenu()
                                        }
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Lên")
                                    }
                                    IconButton(onClick = {
                                        if (index < viewModel.categories.size - 1) {
                                            viewModel.categories.swap(index, index + 1)
                                            viewModel.saveMenu()
                                        }
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xuống")
                                    }
                                }

                                Text(
                                    cat.name,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                        .clickable {
                                            selectedCategoryIndex = index
                                            newCategoryName = cat.name
                                            selectedColor = cat.colorHex
                                        }
                                )

                                Text("❌", color = Color.Red, modifier = Modifier.clickable {
                                    showConfirm = true
                                })

                                if (showConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showConfirm = false },
                                        title = { Text("Xác nhận") },
                                        text = { Text("Bạn có chắc muốn xóa mục này không?") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.removeCategory(cat.name)
                                                showConfirm = false
                                            }) { Text("Xóa") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showConfirm = false }) { Text("Hủy") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = newProductName,
                            onValueChange = { newProductName = it },
                            label = { Text("Tên món", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(64.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PriceInputPad("Giá sản phẩm", productPriceCents) { productPriceCents = it }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DropdownMenuBox(
                                selectedText = selectedCategoryForProduct,
                                options = viewModel.categories.map { it.name to it.name },
                                onColorSelected = { selectedCategoryForProduct = it }
                            )

                            DropdownMenuBox(
                                selectedText = colorOptions.find { it.first == selectedProductColor }?.second ?: "Chọn màu",
                                options = colorOptions,
                                onColorSelected = { selectedProductColor = it }
                            )
                        }


                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button3D(
                                text = "➕ Thêm",
                                onClick = {
                                    if (newProductName.isNotBlank()) {
                                        val price = productPriceCents / 100.0
                                        viewModel.addProduct(newProductName, price, selectedCategoryForProduct, selectedProductColor)
                                        newProductName = ""
                                        productPriceCents = 0
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp), // ✅ chiều cao phù hợp
                                fontSize = 16.sp, // ✅ chữ to hơn cho dễ đọc
                                gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient màu xanh lá đậm
                            )


                            if (selectedProductIndex >= 0) {
                                Button3D(
                                    text = "💾 Cập nhật",
                                    onClick = {
                                        val price = productPriceCents / 100.0
                                        val prod = viewModel.getAllProducts()[selectedProductIndex]
                                        viewModel.updateProduct(
                                            prod.name,
                                            newProductName,
                                            price,
                                            selectedCategoryForProduct,
                                            selectedProductColor
                                        )
                                        selectedProductIndex = -1
                                        newProductName = ""
                                        productPriceCents = 0
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp), // ✅ chiều cao phù hợp
                                    fontSize = 16.sp, // ✅ chữ to hơn cho dễ đọc
                                    gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient màu xanh dương
                                )
                            }
                        }

                    }

                    Spacer(Modifier.width(16.dp))

                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(viewModel.getAllProducts()) { index, product ->
                            var showConfirm by remember { mutableStateOf(false) }

                            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        if (index > 0) viewModel.moveProduct(index, index - 1)
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Lên")
                                    }
                                    IconButton(onClick = {
                                        if (index < viewModel.getAllProducts().size - 1) viewModel.moveProduct(index, index + 1)
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xuống")
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                        .clickable {
                                            selectedProductIndex = index
                                            newProductName = product.name
                                            productPriceCents = (product.price * 100).toInt()
                                            selectedCategoryForProduct = product.category
                                        }
                                ) {
                                    Text(product.name, fontSize = 18.sp)
                                    Text("Giá: %.2f$".format(product.price), fontSize = 14.sp, color = Color.Gray)
                                    Text(product.category, fontSize = 13.sp, color = Color.Gray)
                                }

                                Text("❌", color = Color.Red, modifier = Modifier.clickable {
                                    showConfirm = true
                                })

                                if (showConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showConfirm = false },
                                        title = { Text("Xác nhận") },
                                        text = { Text("Bạn có chắc muốn xóa món này không?") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.removeProduct(
                                                    product.name,
                                                    product.price,
                                                    product.category,
                                                    product.colorHex // ✅ thêm dòng này
                                                )
                                                showConfirm = false
                                            }) { Text("Xóa") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showConfirm = false }) { Text("Hủy") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }


            2 -> {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = extraName,
                            onValueChange = { extraName = it },
                            label = { Text("Tên Extra", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(64.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PriceInputPad("Giá Extra", extraPriceCents) { extraPriceCents = it }

                        Spacer(modifier = Modifier.height(8.dp))

                        DropdownMenuBox(
                            selectedText = selectedExtraCategory,
                            options = viewModel.categories.map { it.name to it.name },
                            onColorSelected = {
                                selectedExtraCategory = it
                                selectedExtraIndex = -1
                                extraName = ""
                                extraPriceCents = 0
                                val listName = it.lowercase().replace(" ", "_") + "_extras"
                                currentExtras = ExtraStorage.loadExtras(context, listName)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button3D(
                            text = if (selectedExtraIndex >= 0) "💾 Cập nhật" else "➕ Thêm",
                            onClick = {
                                val list = currentExtras.toMutableList()
                                if (selectedExtraIndex >= 0) {
                                    list[selectedExtraIndex] = ExtraOption(extraName, extraPriceCents / 100.0)
                                    selectedExtraIndex = -1
                                } else {
                                    list.add(ExtraOption(extraName, extraPriceCents / 100.0))
                                }
                                currentExtras = list
                                extraName = ""
                                extraPriceCents = 0

                                ExtraStorage.saveExtras(
                                    context,
                                    selectedExtraCategory.lowercase().replace(" ", "_") + "_extras",
                                    list
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp), // ✅ chiều cao phù hợp
                            fontSize = 16.sp, // ✅ chữ to hơn cho dễ đọc
                            gradientColors = if (selectedExtraIndex >= 0) {
                                listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient màu xanh dương (Cập nhật)
                            } else {
                                listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient màu xanh lá (Thêm)
                            }
                        )

                    }

                    Spacer(Modifier.width(16.dp))

                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(currentExtras) { index, extra ->
                            var showConfirm by remember { mutableStateOf(false) }

                            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        if (index > 0) {
                                            val updated = currentExtras.toMutableList().apply { swap(index, index - 1) }
                                            currentExtras = updated
                                            ExtraStorage.saveExtras(
                                                context,
                                                selectedExtraCategory.lowercase().replace(" ", "_") + "_extras",
                                                updated
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Lên")
                                    }

                                    IconButton(onClick = {
                                        if (index < currentExtras.size - 1) {
                                            val updated = currentExtras.toMutableList().apply { swap(index, index + 1) }
                                            currentExtras = updated
                                            ExtraStorage.saveExtras(
                                                context,
                                                selectedExtraCategory.lowercase().replace(" ", "_") + "_extras",
                                                updated
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xuống")
                                    }
                                }

                                Text(
                                    extra.name,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                        .clickable {
                                            selectedExtraIndex = index
                                            extraName = extra.name
                                            extraPriceCents = (extra.price * 100).toInt()
                                        }
                                )

                                Text("❌", color = Color.Red, modifier = Modifier.clickable {
                                    showConfirm = true
                                })

                                if (showConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showConfirm = false },
                                        title = { Text("Xác nhận") },
                                        text = { Text("Xóa Extra này?") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                val updated = currentExtras.toMutableList().apply { removeAt(index) }
                                                currentExtras = updated
                                                ExtraStorage.saveExtras(
                                                    context,
                                                    selectedExtraCategory.lowercase().replace(" ", "_") + "_extras",
                                                    updated
                                                )
                                                showConfirm = false
                                            }) { Text("Xóa") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showConfirm = false }) { Text("Hủy") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    // ── Sync preview dialog ───────────────────────────────────────────────────
    if (showSyncPreview) {
        AlertDialog(
            onDismissRequest = { showSyncPreview = false },
            title = { Text("🔄 Sync Menu từ Clover POS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tìm thấy từ Clover POS:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("📁  ${syncPreviewCats.size} danh mục", fontSize = 13.sp)
                            Text("🍜  ${syncPreviewItems.size} món ăn (visible, có danh mục)", fontSize = 13.sp)
                        }
                    }
                    Text("Chọn cách đồng bộ:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Button3D(
                        text = "🔗 Hợp nhất — Thêm mới, giữ menu cũ",
                        onClick = {
                            val (newCats, newProds) = mergeFromClover(
                                syncPreviewCats, syncPreviewItems,
                                viewModel.categories.toList(), viewModel.getAllProducts()
                            )
                            viewModel.replaceMenu(newCats, newProds)
                            syncDoneMsg = "✅ Hợp nhất: ${newCats.size} danh mục · ${newProds.size} món"
                            showSyncPreview = false
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        fontSize = 13.sp,
                        gradientColors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                    )
                    Button3D(
                        text = "♻️ Thay toàn bộ — Xóa menu cũ, dùng Clover",
                        onClick = {
                            val (newCats, newProds) = replaceWithClover(
                                syncPreviewCats, syncPreviewItems,
                                viewModel.categories.toList()
                            )
                            viewModel.replaceMenu(newCats, newProds)
                            syncDoneMsg = "✅ Đã thay toàn bộ: ${newCats.size} danh mục · ${newProds.size} món"
                            showSyncPreview = false
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        fontSize = 13.sp,
                        gradientColors = listOf(Color(0xFFEF5350), Color(0xFFC62828))
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSyncPreview = false }) { Text("Hủy") } }
        )
    }

    if (syncError.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { syncError = "" },
            title = { Text("Lỗi Sync") },
            text  = { Text(syncError) },
            confirmButton = { TextButton(onClick = { syncError = "" }) { Text("OK") } }
        )
    }

    if (syncDoneMsg.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { syncDoneMsg = "" },
            title = { Text("Sync hoàn tất") },
            text  = { Text(syncDoneMsg) },
            confirmButton = { TextButton(onClick = { syncDoneMsg = "" }) { Text("OK") } }
        )
    }
}
