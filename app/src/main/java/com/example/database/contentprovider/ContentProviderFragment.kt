package com.example.database.contentprovider

import android.Manifest
import android.content.ContentResolver
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.database.databinding.FragmentContentProviderBinding
import com.example.database.extension.hasPermission

class ContentProviderFragment : Fragment() {

    private var _binding: FragmentContentProviderBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission ->
        if (hasPermission) {
            rebuildResult()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentContentProviderBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonLoad.setOnClickListener {
            if (requireContext().hasPermission(Manifest.permission.READ_CONTACTS)) {
                rebuildResult()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun rebuildResult() {
        binding.textResult.text =
            requireContext().contentResolver.getContacts().joinToString("\n")
    }

    private fun ContentResolver.getContacts(): List<ContactData> {
        return query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        ).use { cursor ->
            cursor ?: return emptyList()

            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)

            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map {
                    val id = it.getString(idColumn)
                    val name = it.getString(nameColumn)
                    ContactData(id, name, getContactPhones(id))
                }
                .toList()
        }
    }

    private fun ContentResolver.getContactPhones(contactId: String): List<String> {
        return query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        ).use { cursor ->
            cursor ?: return emptyList()

            val phoneColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map {
                    it.getString(phoneColumn)
                }
                .toList()
        }
    }
}