package com.tarunguptaraja.coldemailer.presentation.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.ProductDetails
import com.tarunguptaraja.coldemailer.databinding.ItemShopProductBinding

class ShopAdapter(private val onBuyClick: (ProductDetails) -> Unit) :
    ListAdapter<ProductDetails, ShopAdapter.ProductViewHolder>(ProductDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemShopProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(private val binding: ItemShopProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductDetails) {
            binding.tvProductTitle.text = product.name
            binding.tvProductDesc.text = product.description
            
            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unknown Price"
            binding.btnBuy.text = price

            binding.btnBuy.setOnClickListener {
                onBuyClick(product)
            }
        }
    }

    class ProductDiffCallback : DiffUtil.ItemCallback<ProductDetails>() {
        override fun areItemsTheSame(oldItem: ProductDetails, newItem: ProductDetails): Boolean {
            return oldItem.productId == newItem.productId
        }

        override fun areContentsTheSame(oldItem: ProductDetails, newItem: ProductDetails): Boolean {
            return oldItem == newItem
        }
    }
}
