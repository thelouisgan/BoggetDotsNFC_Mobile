package com.ganlouis.nfc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ganlouis.nfc.models.Product
import com.google.android.material.card.MaterialCardView

class ProductAdapter(
    private val products: List<Product>,
    private val onProductSelected: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.productCardView)
        val imageView: ImageView = view.findViewById(R.id.productImageView)
        val nameTextView: TextView = view.findViewById(R.id.productNameTextView)
        val priceTextView: TextView = view.findViewById(R.id.productPriceTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        holder.nameTextView.text = product.name
        holder.priceTextView.text = "${product.price} eDots"
        Glide.with(holder.imageView.context)
            .load(product.imageUrl)
            .into(holder.imageView)

        holder.cardView.setOnClickListener {
            onProductSelected(product)
        }
    }

    override fun getItemCount() = products.size
}