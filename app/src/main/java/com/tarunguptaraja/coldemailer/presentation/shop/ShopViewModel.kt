package com.tarunguptaraja.coldemailer.presentation.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.tarunguptaraja.coldemailer.BillingManager
import com.tarunguptaraja.coldemailer.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val products: List<ProductDetails> = emptyList(),
    val remainingTokens: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    init {
        observeTokens()
        observeProducts()
        observeBillingErrors()
        
        // Ensure billing connection is active
        billingManager.startBillingConnection()
    }

    private fun observeTokens() {
        viewModelScope.launch {
            tokenManager.tokens.collect { tokens ->
                _uiState.value = _uiState.value.copy(remainingTokens = tokens)
            }
        }
    }

    private fun observeProducts() {
        viewModelScope.launch {
            billingManager.productDetails.collect { products ->
                _uiState.value = _uiState.value.copy(
                    products = products,
                    isLoading = products.isEmpty()
                )
            }
        }
    }

    private fun observeBillingErrors() {
        viewModelScope.launch {
            billingManager.billingError.collect { error ->
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }

    fun buyProduct(activity: android.app.Activity, productDetails: ProductDetails) {
        billingManager.clearError()
        billingManager.launchBillingFlow(activity, productDetails)
    }
}
