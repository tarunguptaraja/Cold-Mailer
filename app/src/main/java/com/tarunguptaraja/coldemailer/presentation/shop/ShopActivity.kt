package com.tarunguptaraja.coldemailer.presentation.shop

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tarunguptaraja.coldemailer.databinding.ActivityShopBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShopActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopBinding
    private val viewModel: ShopViewModel by viewModels()
    private lateinit var adapter: ShopAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityShopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        setupUI()
        observeState()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = ShopAdapter { product ->
            viewModel.buyProduct(this, product)
        }
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCurrentTokens.text = "${state.remainingTokens} Tokens"
                    
                    if (state.isLoading) {
                        binding.progressLoading.visibility = View.VISIBLE
                        binding.rvProducts.visibility = View.GONE
                    } else {
                        binding.progressLoading.visibility = View.GONE
                        binding.rvProducts.visibility = View.VISIBLE
                        adapter.submitList(state.products)
                    }

                    if (state.error != null) {
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = state.error
                    } else {
                        binding.tvError.visibility = View.GONE
                    }
                }
            }
        }
    }
}
