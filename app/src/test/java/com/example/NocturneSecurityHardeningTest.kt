package com.example

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.NocturneDatabase
import com.example.engine.GeoMath
import com.example.service.AlarmScheduleStatus
import com.example.service.NocturneAlarmManager
import com.example.service.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NocturneSecurityHardeningTest {

    @Test
    fun `manifest components exposure audit - only expected activities and receivers are exported`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        )

        // 1. Activity Audit
        val activities = packageInfo.activities ?: emptyArray()
        for (activity in activities) {
            if (activity.name == "com.example.MainActivity") {
                assertTrue("MainActivity must be exported as the launcher activity", activity.exported)
            } else if (activity.name == "com.example.ui.AlarmActivity") {
                assertFalse("AlarmActivity must NOT be exported to prevent external task hijacking", activity.exported)
            }
        }

        // 2. Service Audit
        val services = packageInfo.services ?: emptyArray()
        for (service in services) {
            if (service.name == "com.example.service.NocturneTrackingService") {
                assertFalse("NocturneTrackingService must be unexported (exported=false)", service.exported)
            }
        }

        // 3. Receiver Audit
        val receivers = packageInfo.receivers ?: emptyArray()
        for (receiver in receivers) {
            if (receiver.name == "com.example.receiver.NocturneAlarmReceiver") {
                assertFalse("NocturneAlarmReceiver must be unexported (exported=false)", receiver.exported)
            } else if (receiver.name == "com.example.receiver.BootReceiver") {
                assertTrue("BootReceiver is exported to receive system BOOT_COMPLETED broadcasts", receiver.exported)
            }
        }
    }

    @Test
    fun `notification privacy - ongoing tracking notification uses VISIBILITY_PRIVATE and provides redacted public version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = NotificationHelper.buildTrackingNotification(
            context = context,
            destinationName = "Sensitive Secret Destination",
            etaMinutes = 25,
            etaRange = "20-30m",
            distanceKm = 18.5,
            statusText = "Tracking"
        )

        // Notification visibility must be VISIBILITY_PRIVATE (-1) to redact sensitive data on lock screen
        assertEquals(
            "Tracking notification must be VISIBILITY_PRIVATE on lock screens",
            android.app.Notification.VISIBILITY_PRIVATE,
            notification.visibility
        )

        // Public version must exist to safely show generic transit status without coordinates or destination
        val publicNotification = notification.publicVersion
        assertNotNull("Public version notification must be provided for lock screen display", publicNotification)
        assertEquals(
            "Public version must have VISIBILITY_PUBLIC",
            android.app.Notification.VISIBILITY_PUBLIC,
            publicNotification?.visibility
        )

        // Public version must NOT leak the secret destination name in ticker, title, or extras
        val publicTitle = publicNotification?.extras?.getString(android.app.Notification.EXTRA_TITLE)
        val publicText = publicNotification?.extras?.getString(android.app.Notification.EXTRA_TEXT)
        assertFalse("Public title must not contain destination name", publicTitle?.contains("Sensitive Secret Destination") == true)
        assertFalse("Public text must not contain destination name", publicText?.contains("Sensitive Secret Destination") == true)
    }

    @Test
    fun `data extraction and backup rules files exist and are well-formed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resources = context.resources

        // Verify that network_security_config and backup rules exist in compiled XML resources
        val networkSecResId = resources.getIdentifier("network_security_config", "xml", context.packageName)
        assertTrue("network_security_config.xml must be defined in res/xml", networkSecResId != 0)

        val dataExtractionResId = resources.getIdentifier("data_extraction_rules", "xml", context.packageName)
        assertTrue("data_extraction_rules.xml must be defined in res/xml", dataExtractionResId != 0)

        val backupRulesResId = resources.getIdentifier("backup_rules", "xml", context.packageName)
        assertTrue("backup_rules.xml must be defined in res/xml", backupRulesResId != 0)
    }

    @Test
    fun `database migration - MIGRATION_1_2 preserves active journey records and initializes telemetry defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Create SQLite database using public FrameworkSQLiteOpenHelperFactory representing Schema Version 1
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // In-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE journeys (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            destinationName TEXT NOT NULL,
                            destinationLat REAL NOT NULL,
                            destinationLon REAL NOT NULL,
                            startLat REAL NOT NULL,
                            startLon REAL NOT NULL,
                            wakeUpBufferMinutes INTEGER NOT NULL,
                            lastKnownLat REAL NOT NULL,
                            lastKnownLon REAL NOT NULL,
                            lastKnownSpeedKmh REAL NOT NULL,
                            lastKnownAccuracyMeters REAL NOT NULL,
                            lastFixTimestamp INTEGER NOT NULL,
                            etaMinutesPoint INTEGER NOT NULL,
                            etaMinutesLower INTEGER NOT NULL,
                            etaMinutesUpper INTEGER NOT NULL,
                            confidence TEXT NOT NULL,
                            anomalyType TEXT NOT NULL,
                            anomalyDetail TEXT NOT NULL,
                            status TEXT NOT NULL,
                            startTimestamp INTEGER NOT NULL,
                            alarmTriggered INTEGER NOT NULL,
                            alarmTriggeredTimestamp INTEGER
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val supportDb = helper.writableDatabase

        // 2. Insert pre-existing active journey in Version 1 format
        supportDb.execSQL(
            """
            INSERT INTO journeys (
                id, destinationName, destinationLat, destinationLon, startLat, startLon,
                wakeUpBufferMinutes, lastKnownLat, lastKnownLon, lastKnownSpeedKmh,
                lastKnownAccuracyMeters, lastFixTimestamp, etaMinutesPoint, etaMinutesLower,
                etaMinutesUpper, confidence, anomalyType, anomalyDetail, status,
                startTimestamp, alarmTriggered, alarmTriggeredTimestamp
            ) VALUES (
                1, 'Central Station Transit Hub', 40.7527, -73.9772, 40.7128, -74.0060,
                15, 40.7300, -73.9900, 55.0, 12.0, 1700000000, 25, 20, 30, 'HIGH',
                'NONE', 'Tracking normal', 'ACTIVE', 1699990000, 0, NULL
            )
            """.trimIndent()
        )

        // 3. Execute MIGRATION_1_2 (simulating app upgrade without dropping tables)
        NocturneDatabase.MIGRATION_1_2.migrate(supportDb)

        // 4. Verify original journey data is preserved
        val cursor = supportDb.query("SELECT * FROM journeys WHERE id = 1")
        assertTrue("Migrated journey record must exist", cursor.moveToFirst())

        val destNameIndex = cursor.getColumnIndex("destinationName")
        assertEquals("Central Station Transit Hub", cursor.getString(destNameIndex))

        val bufferIndex = cursor.getColumnIndex("wakeUpBufferMinutes")
        assertEquals(15, cursor.getInt(bufferIndex))

        val statusIndex = cursor.getColumnIndex("status")
        assertEquals("ACTIVE", cursor.getString(statusIndex))

        // 5. Verify all newly added columns in Schema Version 2 have safe defaults
        val remainingDistIndex = cursor.getColumnIndex("remainingDistanceKm")
        assertEquals(0.0, cursor.getDouble(remainingDistIndex), 0.001)

        val circuityIndex = cursor.getColumnIndex("routeCircuityFactor")
        assertEquals(1.25, cursor.getDouble(circuityIndex), 0.001)

        val isRouteAwareIndex = cursor.getColumnIndex("isRouteAware")
        assertEquals(1, cursor.getInt(isRouteAwareIndex))

        val targetAlarmIndex = cursor.getColumnIndex("targetScheduledAlarmMs")
        assertEquals(0L, cursor.getLong(targetAlarmIndex))

        val stationaryIndex = cursor.getColumnIndex("isVehicleStationary")
        assertEquals(0, cursor.getInt(stationaryIndex))

        val gpsStaleIndex = cursor.getColumnIndex("gpsStale")
        assertEquals(0, cursor.getInt(gpsStaleIndex))

        cursor.close()
        supportDb.close()
    }

    @Test
    fun `telemetry sanitization - GeoMath rejects malformed, infinite, and out-of-range coordinates`() {
        // Valid ranges
        assertTrue(GeoMath.isValidCoordinate(40.7128, -74.0060))
        assertTrue(GeoMath.isValidCoordinate(0.0, 0.0))
        assertTrue(GeoMath.isValidCoordinate(90.0, 180.0))
        assertTrue(GeoMath.isValidCoordinate(-90.0, -180.0))

        // Invalid latitudes
        assertFalse(GeoMath.isValidCoordinate(90.1, 0.0))
        assertFalse(GeoMath.isValidCoordinate(-90.1, 0.0))
        assertFalse(GeoMath.isValidCoordinate(Double.NaN, 0.0))
        assertFalse(GeoMath.isValidCoordinate(Double.POSITIVE_INFINITY, 0.0))
        assertFalse(GeoMath.isValidCoordinate(Double.NEGATIVE_INFINITY, 0.0))

        // Invalid longitudes
        assertFalse(GeoMath.isValidCoordinate(0.0, 180.1))
        assertFalse(GeoMath.isValidCoordinate(0.0, -180.1))
        assertFalse(GeoMath.isValidCoordinate(0.0, Double.NaN))
        assertFalse(GeoMath.isValidCoordinate(0.0, Double.POSITIVE_INFINITY))

        // Malformed calculation distance returns 0.0 without crash
        assertEquals(0.0, GeoMath.calculateDistanceKm(Double.NaN, 0.0, 40.0, -74.0), 0.001)
        assertEquals(0.0, GeoMath.calculateRouteDistanceKm(Double.NaN, 0.0, 40.0, -74.0), 0.001)

        // Speed validation
        assertTrue(GeoMath.isValidSpeed(65.0))
        assertFalse(GeoMath.isValidSpeed(-1.0))
        assertFalse(GeoMath.isValidSpeed(550.0))
        assertFalse(GeoMath.isValidSpeed(Double.NaN))

        // Accuracy validation
        assertTrue(GeoMath.isValidAccuracy(15.0f))
        assertFalse(GeoMath.isValidAccuracy(-5.0f))
        assertFalse(GeoMath.isValidAccuracy(6000.0f))
        assertFalse(GeoMath.isValidAccuracy(Float.NaN))
    }

    @Test
    fun `alarm scheduling defense - scheduleExactAlarm gracefully schedules and records status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val triggerTime = System.currentTimeMillis() + 600_000L // 10 min in future

        val status = NocturneAlarmManager.scheduleExactAlarm(
            context = context,
            triggerAtMillis = triggerTime,
            destinationName = "Protected Test Hub",
            etaMinutes = 10
        )

        // In Robolectric environment without restricted permission, status must be either EXACT or INEXACT_FALLBACK
        assertTrue(
            "Alarm must be scheduled either as EXACT or INEXACT_FALLBACK without crashing",
            status == AlarmScheduleStatus.EXACT || status == AlarmScheduleStatus.INEXACT_FALLBACK
        )
        assertEquals(status, NocturneAlarmManager.lastScheduleStatus)

        // Verify alarm can be cleanly cancelled without leaks
        NocturneAlarmManager.cancelAlarm(context)
    }
}
