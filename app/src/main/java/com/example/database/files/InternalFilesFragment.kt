package com.example.database.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.database.databinding.FragmentFilesBinding
import com.example.database.extension.showToast
import java.io.IOException
import java.util.*

class InternalFilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (saveFileToInternalStorage(bitmap)) {
            loadImages()
        } else {
            requireContext().showToast("Failed to save photo")
        }
    }

    private val imagesAdapter by lazy {
        ImagesAdapter<ImageItem.Internal>(requireContext()) { item ->
            if (deleteFileFromInternalStorage(item.fileName)) {
                loadImages()
            } else {
                requireContext().showToast("Failed to delete photo")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentFilesBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            recyclerView.layoutManager =
                StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
            recyclerView.adapter = imagesAdapter

            buttonSave.setOnClickListener {
                takePhotoLauncher.launch(null)
            }
        }

        loadImages()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadImages() {
        imagesAdapter.submitList(openFilesFromInternalStorage())
    }

    private fun saveFileToInternalStorage(bitmap: Bitmap): Boolean {
        val fileName = UUID.randomUUID().toString()
        return try {
            requireContext().openFileOutput("$fileName.jpg", Context.MODE_PRIVATE).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't save bitmap")
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun openFilesFromInternalStorage(): List<ImageItem.Internal> {
        return requireContext()
            .filesDir
            .listFiles()
            ?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }
            ?.map { file ->
                val bytes = file.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ImageItem.Internal(file.name, bitmap)
            }
            ?: emptyList()
    }

    private fun deleteFileFromInternalStorage(fileName: String): Boolean {
        return try {
            requireContext().deleteFile(fileName)
        } catch (e: IOException) {
            false
        }
    }
}