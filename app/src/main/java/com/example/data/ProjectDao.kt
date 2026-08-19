package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project_state ORDER BY timestamp DESC")
    suspend fun getAllProjects(): List<ProjectState>

    @Query("SELECT * FROM project_state WHERE id = :id LIMIT 1")
    suspend fun getProjectState(id: Int): ProjectState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectState(state: ProjectState): Long

    @Query("DELETE FROM project_state WHERE id = :id")
    suspend fun deleteProject(id: Int)
}
