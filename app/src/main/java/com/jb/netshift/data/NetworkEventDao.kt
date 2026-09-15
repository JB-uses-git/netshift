package com.jb.netshift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NetworkEventDao {
    @Insert
    fun insert(event: NetworkEvent)

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC")
    fun getAllEvents(): List<NetworkEvent>

    @Query("DELETE FROM network_events")
    fun clearAll()
}
