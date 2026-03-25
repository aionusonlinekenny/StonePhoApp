package com.example.stonephopro.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stonephopro.model.*
import com.example.stonephopro.utils.InvoiceStorage
import com.example.stonephopro.utils.MenuStorage
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class OrderViewModel(private val context: Context) : ViewModel() {

    private val allProducts = mutableStateListOf<Product>()
    val categories = mutableStateListOf<Category>()

    var selectedCategory = mutableStateOf(Category("Regular Phở", "#90CAF9"))
    var cart = mutableStateListOf<Product>()

    var isMenuLoaded = mutableStateOf(false)
    // ✅ Dùng để lưu toàn bộ extra đã preload cho từng mục lớn
    var loadedExtrasMap: Map<String, List<ExtraOption>> = emptyMap()

    // ✅ Dùng để lưu các hóa đơn đã load trước nếu cần
    var preloadedInvoices: List<Invoice> = emptyList()
    // ✅ Setter để set danh sách sản phẩm khi load xong từ IntroScreen
    fun setProducts(products: List<Product>) {
        allProducts.clear()
        allProducts.addAll(products)
    }
    init {
        loadMenuAsync()
    }

    private fun loadMenuAsync() {
        viewModelScope.launch(Dispatchers.IO) {

            val (savedCategories, savedProducts) = MenuStorage.loadMenu(context)
            delay(200) // Cho thread chính "thở"

            withContext(Dispatchers.Main) {
                categories.clear()
                categories.addAll(savedCategories)

                if (categories.isEmpty()) {
                    categories.addAll(
                        listOf(
                            Category("Regular Phở", "#90CAF9"),
                            Category("Nước", "#A5D6A7"),
                            Category("Món Ăn Khác", "#F48FB1")
                        )
                    )
                }

                selectedCategory.value = categories.first()
                allProducts.clear()
                allProducts.addAll(savedProducts)

                isMenuLoaded.value = true
            }
        }
    }

    fun buildInvoiceContentFormatted(invoiceId: String): String {
        val cartItems = cart.groupBy { it }.mapValues { it.value.size }
        val subtotal = cartItems.entries.sumOf { it.key.price * it.value }
        val tax = subtotal * 0.08
        val total = subtotal + tax
        val sb = StringBuilder()

        sb.append("       STONE PHO POS\n")
        sb.append("      1525 Baytree Rd\n")
        sb.append("       Valdosta, GA\n")
        sb.append("------------------------------\n")
        sb.append("Invoice ID: $invoiceId\n")
        sb.append("Date: ${InvoiceStorage.getTodayDate()}  Time: ${InvoiceStorage.getCurrentTime()}\n")
        sb.append("------------------------------\n")

        cartItems.forEach { (product, qty) ->
            val line = "%-20s %2dx %5.2f".format(product.name.take(20), qty, product.price * qty)
            sb.appendLine(line)
        }

        sb.append("------------------------------\n")
        sb.append("Subtotal:        %.2f$\n".format(subtotal))
        sb.append("Thuế (8%%):       %.2f$\n".format(tax))
        sb.append("TOTAL:           %.2f$\n".format(total))
        sb.append("------------------------------\n")
        sb.append("  Thank you for your visit!\n\n")

        return sb.toString()
    }

    fun updateProduct(oldName: String, newName: String, newPrice: Double, newCategory: String, newColorHex: String) {
        val index = allProducts.indexOfFirst { it.name == oldName }
        if (index != -1) {
            allProducts[index] = allProducts[index].copy(
                name = newName,
                price = newPrice,
                category = newCategory,
                colorHex = newColorHex
            )
            saveMenu()
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        val index = categories.indexOfFirst { it.name == oldName }
        if (index != -1) {
            val oldCategory = categories[index]
            categories[index] = oldCategory.copy(name = newName)
        }

        val updatedProducts = allProducts.map {
            if (it.category == oldName) it.copy(category = newName) else it
        }

        allProducts.clear()
        allProducts.addAll(updatedProducts)

        saveMenu()
    }

    fun getAllProducts(): List<Product> = allProducts

    fun addCustomProduct(name: String, price: Double) {
        val customProduct = Product(
            id = UUID.randomUUID().hashCode(),
            name = name,
            price = price,
            category = "QuickPay"
        )
        cart.add(customProduct)
    }

    fun saveCurrentInvoice(
        context: Context,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double
    ): String {
        val id = InvoiceStorage.generateInvoiceId()
        val date = InvoiceStorage.getTodayDate()
        val time = InvoiceStorage.getCurrentTime()

        val invoice = Invoice(
            id = id,
            items = cart.toList(),
            subtotal = subtotal,
            discount = discount, // ✅ Đảm bảo lưu đúng discount
            tax = tax,
            total = total,
            date = date,
            time = time
        )

        InvoiceStorage.saveInvoice(context, invoice)
        cart.clear()
        return id
    }

    fun addToCart(product: Product) {
        cart.add(product)
    }

    fun removeFromCart(product: Product) {
        cart.remove(product)
    }

    fun addCategory(name: String, colorHex: String) {
        if (name.isNotBlank() && categories.none { it.name == name }) {
            categories.add(Category(name, colorHex))
            saveMenu()
        }
    }

    fun removeCategory(name: String) {
        categories.removeAll { it.name == name }
        allProducts.removeAll { it.category == name }
        saveMenu()
    }

    fun addProduct(name: String, price: Double, category: String, colorHex: String) {
        val nextId = (allProducts.maxOfOrNull { it.id } ?: 0) + 1
        allProducts.add(Product(nextId, name, price, category, colorHex = colorHex))
        saveMenu()
    }

    fun removeProduct(name: String, price: Double, category: String, colorHex: String) {
        val iterator = allProducts.iterator()
        while (iterator.hasNext()) {
            val product = iterator.next()
            if (
                product.name == name &&
                product.price == price &&
                product.category == category &&
                product.colorHex == colorHex
            ) {
                iterator.remove()
                break
            }
        }
        saveMenu()
    }


    fun moveProduct(from: Int, to: Int) {
        allProducts.swap(from, to)
        saveMenu()
    }

    fun saveMenu() {
        MenuStorage.saveMenu(context, categories, allProducts)
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) {
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}
