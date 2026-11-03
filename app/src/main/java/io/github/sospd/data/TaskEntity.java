package io.github.sospd.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String kind = "SCOUT";
    @NonNull public String status = "RUNNING";
    @NonNull public String requestJson = "";
    @NonNull public String label = "";
    public long startSeed;
    public long nextSeed;
    public long searchedCount;
    public long startedAt;
    public long updatedAt;
    public long elapsedMillis;
    public String reportJson;
    public String error;
    public String checkpointJson;
}

