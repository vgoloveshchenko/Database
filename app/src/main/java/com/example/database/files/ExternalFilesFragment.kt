package com.example.database.files

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.database.databinding.FragmentFilesBinding
import com.example.database.extension.hasPermission
import com.example.database.extension.showToast
import java.io.IOException
import java.util.*

class ExternalFilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val hasReadPermission: Boolean
        get () = requireContext().hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val hasWritePermission: Boolean
        get() = requireContext().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val isPhotoSaved = if (hasWritePermission) {
            saveFileToExternalStorage(bitmap)
        } else {
            false
        }

        if (isPhotoSaved) {
            loadImages()
        } else {
            requireContext().showToast("Failed to save photo")
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
            loadImages()
        } else {
            requireContext().showToast("Don't have access to read external storage")
        }
    }

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadImages()
        } else {
            requireContext().showToast("Failed to delete photo")
        }
    }

    private val imagesAdapter by lazy {
        ImagesAdapter<ImageItem.External>(requireContext()) { item ->
            deleteFileFromExternalStorage(item.contentUri)
        }
    }

    private val contentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            if (hasReadPermission) {
                loadImages()
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

        requestPermissionsIfNeeded()

        with(binding) {
            recyclerView.layoutManager =
                StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
            recyclerView.adapter = imagesAdapter

            buttonSave.setOnClickListener {
                takePhoto.launch(null)
            }
        }

        loadImages()
    }

    override fun onStart() {
        super.onStart()
        requireContext().contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().contentResolver.unregisterContentObserver(contentObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = listOfNotNull(
            Manifest.permission.READ_EXTERNAL_STORAGE.takeIf { !hasReadPermission },
            Manifest.permission.WRITE_EXTERNAL_STORAGE.takeIf { !hasWritePermission }
        )

        if (permissions.isNotEmpty()) {
            requestPermissions.launch(permissions.toTypedArray())
        }
    }

    private fun loadImages() {
        if (hasReadPermission) {
            imagesAdapter.submitList(openFilesFromExternalStorage())
        }
    }

    private fun saveFileToExternalStorage(bitmap: Bitmap): Boolean {
        val fileName = UUID.randomUUID().toString()
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }

        return try {
            val contentResolver = requireContext().contentResolver

            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri)?.use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create MediaStore entity")
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun openFilesFromExternalStorage(): List<ImageItem.External> {
        val imagesCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)

        return requireContext().contentResolver.query(
            imagesCollection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        ).use { cursor ->
            cursor ?: return emptyList()

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map {
                    val id = cursor.getLong(idColumn)
                    val fileName = cursor.getString(displayNameColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    ImageItem.External(fileName, contentUri)
                }
                .toList()
        }
    }

    private fun deleteFileFromExternalStorage(photoUri: Uri) {
        val contentResolver = requireContext().contentResolver

        try {
            contentResolver.delete(photoUri, null, null)
        } catch (e: SecurityException) {
            val intentSender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val recoverableSecurityException = e as? RecoverableSecurityException
                    recoverableSecurityException?.userAction?.actionIntent?.intentSender
                }
                else -> null
            }
            intentSender?.let { sender ->
                intentSenderLauncher.launch(
                    IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }
}