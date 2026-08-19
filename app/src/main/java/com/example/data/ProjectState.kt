package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_state")
data class ProjectState(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "Lienzo sin título",
    val thumbnail: String? = null,
    val stateJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
