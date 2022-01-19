package com.example.database.files

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.database.databinding.ItemImageBinding

class ImagesAdapter<T : ImageItem>(
    context: Context,
    private val onLongItemClicked: (T) -> Unit
) : ListAdapter<T, ImageViewHolder<T>>(ImageItemDiffCallback()) {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder<T> {
        return ImageViewHolder(
            binding = ItemImageBinding.inflate(layoutInflater, parent, false),
            onLongItemClicked = onLongItemClicked
        )
    }

    override fun onBindViewHolder(holder: ImageViewHolder<T>, position: Int) {
        holder.bind(getItem(position))
    }

    private class ImageItemDiffCallback<T : ImageItem> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.fileName == newItem.fileName
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return when {
                oldItem is ImageItem.Internal && newItem is ImageItem.Internal -> {
                    oldItem.bitmap.sameAs(newItem.bitmap)
                }
                oldItem is ImageItem.External && newItem is ImageItem.External -> {
                    oldItem.contentUri == newItem.contentUri
                }
                else -> false
            }
        }
    }
}

class ImageViewHolder<T : ImageItem>(
    private val binding: ItemImageBinding,
    private val onLongItemClicked: (T) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: T) {
        with(binding.image) {
            when (item) {
                is ImageItem.External -> setImageURI(item.contentUri)
                is ImageItem.Internal -> setImageBitmap(item.bitmap)
            }

            setOnLongClickListener {
                onLongItemClicked(item)
                true
            }
        }
    }
}