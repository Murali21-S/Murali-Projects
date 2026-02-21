package com.example.medihelp.model

data class Doctor(
    val id: String = "",
    val name: String = "",
    val specialty: String = "",
    val availableSlots: List<String> = emptyList(),
    val reviews: List<String> = emptyList()
) 