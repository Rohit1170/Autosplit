package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A category the user taught the app for one merchant (see MerchantCategorizer).
 * On-device only. Written whenever the user picks a category by hand; read
 * when a NEW SMS / Scan & Pay expense is saved. Never rewrites existing rows.
 */
@Entity(tableName = "merchant_category_map")
data class MerchantCategory(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "merchant_key")
    val merchantKey: String,
    val category: String,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Dao
interface MerchantCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantCategory)

    @Query("UPDATE merchant_category_map SET category = :to WHERE category = :from")
    suspend fun recategorize(from: String, to: String)

    @Query("SELECT category FROM merchant_category_map WHERE merchant_key = :merchantKey LIMIT 1")
    suspend fun categoryFor(merchantKey: String): String?
}
