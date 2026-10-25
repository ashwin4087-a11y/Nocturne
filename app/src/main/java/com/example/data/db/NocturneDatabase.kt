package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.JourneyEntity
import com.example.data.model.ObservationEntity

@Database(
    entities = [JourneyEntity::class, ObservationEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NocturneDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun observationDao(): ObservationDao

    companion object {
        @Volatile
        private var INSTANCE: NocturneDatabase? = null

        /**
         * Migration from Schema Version 1 to Version 2:
         * Preserves existing active journeys and journey history during upgrades
         * by adding newly supported telemetry and state columns with safe default values.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journeys ADD COLUMN remainingDistanceKm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN straightLineDistanceKm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN routeCircuityFactor REAL NOT NULL DEFAULT 1.25")
                db.execSQL("ALTER TABLE journeys ADD COLUMN isRouteAware INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE journeys ADD COLUMN targetScheduledAlarmMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN isVehicleStationary INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN stationaryDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN gpsStale INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from Schema Version 2 to Version 3:
         * Adds Google Maps routing reference metadata and distance source classification.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journeys ADD COLUMN routeDistanceSource TEXT NOT NULL DEFAULT 'ESTIMATED_ROUTE'")
                db.execSQL("ALTER TABLE journeys ADD COLUMN referenceRouteSummary TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from Schema Version 3 to Version 4:
         * Adds route deviation tracking, transport mode, and smart ticket import metadata.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journeys ADD COLUMN deviationState TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE journeys ADD COLUMN crossTrackDistanceKm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE journeys ADD COLUMN transportMode TEXT NOT NULL DEFAULT 'BUS'")
                db.execSQL("ALTER TABLE journeys ADD COLUMN ticketPnr TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE journeys ADD COLUMN scheduledArrivalTimestamp INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE journeys ADD COLUMN originName TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): NocturneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NocturneDatabase::class.java,
                    "nocturne_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
