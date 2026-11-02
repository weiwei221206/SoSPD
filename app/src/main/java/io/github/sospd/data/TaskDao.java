package io.github.sospd.data;

import androidx.room.*;
import java.util.List;

@Dao
public interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void save(TaskEntity task);
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC LIMIT 30") List<TaskEntity> recent();
    @Query("SELECT * FROM tasks WHERE id = :id") TaskEntity find(String id);
    @Query("UPDATE tasks SET status = 'INTERRUPTED' WHERE status = 'RUNNING'") void markInterrupted();
}

