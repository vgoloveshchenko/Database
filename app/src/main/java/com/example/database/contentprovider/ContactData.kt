package com.example.database.contentprovider

data class ContactData(
    val id: String,
    val name: String,
    val phones: List<String>
)