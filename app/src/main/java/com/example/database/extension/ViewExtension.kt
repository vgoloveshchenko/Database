package com.example.database.extension

import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.getTextOrSetError(): String? {
    return editText?.text?.toString()
        ?.ifBlank {
            error = "Field is empty"
            null
        }
}