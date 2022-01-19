package com.example.database.fileprovider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.database.databinding.FragmentFileProviderBinding
import java.io.File

class FileProviderFragment : Fragment() {

    private var _binding: FragmentFileProviderBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val imageLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        binding.image.setImageBitmap(bitmap)
        saveImage(bitmap)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentFileProviderBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {

            getImageFile()?.let { file ->
                val bytes = file.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                image.setImageBitmap(bitmap)
            }

            buttonImage.setOnClickListener {
                imageLauncher.launch(null)
            }
            buttonSendImage.setOnClickListener {
                val imageFile = getImageFile() ?: return@setOnClickListener
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.database.provider",
                    imageFile
                )
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                requireContext().startActivity(intent)
            }

            editText.setText(getTextFromFile())

            buttonSendFile.setOnClickListener {
                saveFile(editText.text?.toString() ?: "")
                val textFile = getTextFile() ?: return@setOnClickListener
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.database.provider",
                    textFile
                )
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                requireContext().startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveImage(bitmap: Bitmap) {
        requireContext()
            .openFileOutput(IMAGE_NAME, Context.MODE_PRIVATE)
            .use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                    error("image not saved")
                }
            }
    }

    private fun getImageFile(): File? {
        return requireContext()
            .filesDir
            .listFiles()
            ?.find { it.name == IMAGE_NAME }
    }

    private fun saveFile(text: String) {
        requireContext()
            .openFileOutput(FILE_NAME, Context.MODE_PRIVATE)
            .use { stream ->
                stream.write(text.toByteArray())
            }
    }

    private fun getTextFile(): File? {
        return requireContext()
            .filesDir
            ?.listFiles()
            ?.find { it.name == FILE_NAME }
    }

    private fun getTextFromFile(): String? {
        return getTextFile()?.readText()
    }

    companion object {
        private const val IMAGE_NAME = "image.jpg"
        private const val FILE_NAME = "text.txt"
    }
}