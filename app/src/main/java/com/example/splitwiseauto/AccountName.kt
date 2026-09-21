package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

// Carried forward from a prior build that already shipped this table to
// disk (see AppDatabase's MIGRATION_10_11 comment) — declaring it as a
// real Room entity, matching the on-device structure exactly, is what
// lets Room's schema-identity check pass again on installs that already
// have this table. Not yet wired into any screen/DAO.
@Entity(tableName = "account_names")
data class AccountName(
    @PrimaryKey
    val idKey: String,
    val displayName: String
)
