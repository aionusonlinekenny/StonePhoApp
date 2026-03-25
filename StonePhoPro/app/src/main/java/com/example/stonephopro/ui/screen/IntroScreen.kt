package com.example.stonephopro.ui.screen

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.R
import com.example.stonephopro.model.ExtraOption
import com.example.stonephopro.utils.*
import com.example.stonephopro.viewmodel.OrderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun IntroScreen(
    viewModel: OrderViewModel,
    onReady: () -> Unit
) {
    val context = LocalContext.current

    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Đang khởi động...") }

    LaunchedEffect(Unit) {
        progress = 0f
        statusText = "Đang tải dữ liệu menu..."

        val (menuCategories, products) = withContext(Dispatchers.IO) {
            MenuStorage.loadMenu(context)
        }
        viewModel.categories.clear()
        viewModel.categories.addAll(menuCategories)
        viewModel.setProducts(products)

        progress = 0.3f
        statusText = "Đang tải extra items..."

        val extraMap = mutableMapOf<String, List<ExtraOption>>()
        menuCategories.forEachIndexed { index, cat ->
            val extras = withContext(Dispatchers.IO) {
                ExtraStorage.loadExtras(context, cat.name.lowercase().replace(" ", "_") + "_extras")
            }
            extraMap[cat.name] = extras
            progress = 0.3f + (index + 1) / (menuCategories.size.toFloat() * 2f)
            delay(200)
        }

        viewModel.loadedExtrasMap = extraMap

        progress = 0.8f
        statusText = "Đang tải hóa đơn gần đây..."
        delay(500)

        val todayInvoices = withContext(Dispatchers.IO) {
            InvoiceStorage.loadInvoices(context, InvoiceStorage.getTodayDate())
        }
        viewModel.preloadedInvoices = todayInvoices

        progress = 0.95f
        statusText = "Đang chuẩn bị giao diện..."

        delay(500)
        progress = 1f
        onReady()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hình nền intro
        Image(
            painter = painterResource(id = R.drawable.intro_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Thanh tiến trình và trạng thái load
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.width(250.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
        }
    }
}
