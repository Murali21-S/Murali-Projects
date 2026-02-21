package com.example.medihelp.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "patient", // or "doctor"
    val healthHistory: String = ""
) 