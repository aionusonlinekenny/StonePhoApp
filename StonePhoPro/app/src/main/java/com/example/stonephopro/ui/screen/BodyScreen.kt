package com.example.stonephopro.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.Product
import com.example.stonephopro.viewmodel.OrderViewModel
import kotlin.math.ceil
import com.google.accompanist.pager.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@SuppressLint("DefaultLocale")
@Composable
fun BodyScreen(viewModel: OrderViewModel) {
    val selectedCategory by viewModel.selectedCategory
    val allProducts = viewModel.getAllProducts()

    val products = remember(selectedCategory, allProducts) {
        allProducts.filter { it.category.equals(selectedCategory.name, ignoreCase = true) }
    }

    val pageSize = 16
    val totalPages = ceil(products.size / pageSize.toDouble()).toInt()
    val pagerState = rememberPagerState()

    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showExtraScreen by remember { mutableStateOf(false) }

    if (showExtraScreen && selectedProduct != null) {
        ExtraSelectorScreen(
            product = selectedProduct!!,
            onConfirm = { productWithExtra ->
                viewModel.addToCart(productWithExtra)
                selectedProduct = null
                showExtraScreen = false
            },
            onBack = {
                selectedProduct = null
                showExtraScreen = false
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        HorizontalPager(
            count = totalPages,
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val itemsOnPage = products.drop(page * pageSize).take(pageSize)

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(itemsOnPage) { product ->
                    val baseColor = Color(android.graphics.Color.parseColor(product.colorHex))
                    val gradientColors = listOf(
                        baseColor.copy(alpha = 0.95f),
                        baseColor.copy(alpha = 1f)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clickable {
                                selectedProduct = product
                                showExtraScreen = true
                            }
                            .padding(4.dp)
                            .shadow(8.dp, shape = MaterialTheme.shapes.medium, clip = false), // Shadow for 3D effect
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent // Set transparent to use gradient
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = gradientColors,
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(0f, 100f)
                                    )
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = product.name,
                                    fontSize = 20.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                )
                                val priceStr = String.format("%.2f", product.price).split(".")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        "${priceStr[0]}.",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                    Text(
                                        priceStr[1] + "$",
                                        fontSize = 12.sp,
                                        color = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
        )
    }
}
