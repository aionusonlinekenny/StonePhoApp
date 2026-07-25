package com.example.stonephopro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stonephopro.utils.clover.CloverAuthManager
import kotlinx.coroutines.launch

// Activity duy nhất có nhiệm vụ bắt deep link callback từ Clover OAuth
// stonepho://clover/oauth?merchant_id=XXX&client_id=YYY&code=ZZZ
class CloverOAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent) {
        val data = intent.data
        if (data == null) { finish(); return }

        val code       = data.getQueryParameter("code")
        val merchantId = data.getQueryParameter("merchant_id") ?: ""

        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Kết nối Clover thất bại: không nhận được code.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val token = CloverAuthManager.exchangeCodeForToken(code)
            if (token != null) {
                CloverAuthManager.saveAuth(this@CloverOAuthActivity, token, merchantId)
                Toast.makeText(this@CloverOAuthActivity, "✅ Đã kết nối Clover thành công!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CloverOAuthActivity, "❌ Không lấy được access token từ Clover.", Toast.LENGTH_LONG).show()
            }
            // Quay về MainActivity (app đang chạy ở background)
            val backIntent = Intent(this@CloverOAuthActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("clover_auth_done", true)
            }
            startActivity(backIntent)
            finish()
        }
    }
}
