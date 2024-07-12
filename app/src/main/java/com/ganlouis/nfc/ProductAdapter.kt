package com.ganlouis.nfc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ganlouis.nfc.models.Product
import com.google.android.material.card.MaterialCardView

class ProductAdapter(
    private val products: List<Product>,
    private val onProductSelected: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.productCardView)
        val imageView: ImageView = view.findViewById(R.id.productImageView)
        val nameTextView: TextView = view.findViewById(R.id.productNameTextView)
        val priceTextView: TextView = view.findViewById(R.id.productPriceTextView)

        fun bind(product: Product, isSelected: Boolean) {
            nameTextView.text = product.name
            priceTextView.text = "${product.price} eDots"
            Glide.with(itemView.context).load(product.imageUrl).into(imageView)

            cardView.background = ContextCompat.getDrawable(
                itemView.context,
                if (isSelected) R.drawable.card_background_selected
                else R.drawable.card_background_unselected
            )

            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    setSelectedPosition(position)
                    onProductSelected(product)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position], position == selectedPosition)
    }

    override fun getItemCount() = products.size

    private fun setSelectedPosition(position: Int) {
        val previousSelected = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousSelected)
        notifyItemChanged(selectedPosition)
    }
}