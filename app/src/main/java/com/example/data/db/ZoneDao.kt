package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ParkingZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM parking_zones ORDER BY districtCode ASC, sectorCode ASC")
    fun getAllZones(): Flow<List<ParkingZoneEntity>>

    @Query("SELECT * FROM parking_zones")
    suspend fun getAllZonesDirect(): List<ParkingZoneEntity>

    @Query("SELECT * FROM parking_zones WHERE districtCode = :districtCode")
    suspend fun getZonesByDistrict(districtCode: String): List<ParkingZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ParkingZoneEntity>)

    @Query("SELECT COUNT(*) FROM parking_zones")
    suspend fun getZoneCount(): Int

    @Query("DELETE FROM parking_zones")
    suspend fun clearAll()
}
