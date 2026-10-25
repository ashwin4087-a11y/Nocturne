package com.example.engine

import com.example.data.model.ExtractedTicket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object TicketParser {

    /**
     * Parses raw ticket text (from PDF, OCR, clipboard, or share intent) into a structured ExtractedTicket.
     * Guaranteed local-first processing, strict field validation, and honest confidence scoring.
     */
    fun parse(rawText: String): ExtractedTicket {
        val sanitized = rawText.trim()
        if (sanitized.isEmpty()) {
            return ExtractedTicket(
                confidenceScore = 0f,
                rawTextSnippet = "",
                ambiguousFields = listOf("Empty ticket input")
            )
        }

        val lines = sanitized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ambiguous = mutableListOf<String>()

        // 1. Detect Transport Mode & Operator
        var mode = "BUS"
        var operator: String? = null
        val lowerText = sanitized.lowercase()

        val hasTrainSignal = lowerText.contains("train") || lowerText.contains("rail") || lowerText.contains("irctc") ||
                lowerText.contains("amtrak") || lowerText.contains("sncf") || lowerText.contains("berth")
        val hasBusSignal = lowerText.contains("bus") || lowerText.contains("volvo") ||
                lowerText.contains("redbus") || lowerText.contains("flixbus") || lowerText.contains("greyhound") ||
                lowerText.contains("travels")

        when {
            hasBusSignal && !hasTrainSignal -> {
                mode = "BUS"
                operator = when {
                    lowerText.contains("flixbus") -> "FlixBus"
                    lowerText.contains("greyhound") -> "Greyhound"
                    lowerText.contains("kpn") -> "KPN Travels"
                    lowerText.contains("redbus") -> "Intercity Bus (redBus)"
                    else -> "Intercity Bus Operator"
                }
            }
            hasTrainSignal -> {
                mode = "TRAIN"
                operator = when {
                    lowerText.contains("irctc") || lowerText.contains("indian railway") -> "Indian Railways / IRCTC"
                    lowerText.contains("amtrak") -> "Amtrak"
                    lowerText.contains("sncf") -> "SNCF"
                    else -> "Rail Transit Operator"
                }
            }
            lowerText.contains("junction") || lowerText.contains("coach") -> {
                mode = "TRAIN"
                operator = "Rail Transit Operator"
            }
            else -> {
                mode = "BUS"
            }
        }

        // 2. Extract PNR / Booking Reference
        // Matches patterns like: PNR: 1234567890, PNR No: 452-1234567, Booking Ref: ABC123D, Ticket #: 892312
        var pnr: String? = null
        val pnrRegex = Pattern.compile("(?i)(?:pnr|booking\\s*(?:ref|id|no)?|ticket\\s*(?:no|id|#)?|reservation\\s*no)[:\\s#-]*([a-z0-9-]{5,15})")
        val pnrMatcher = pnrRegex.matcher(sanitized)
        if (pnrMatcher.find()) {
            pnr = pnrMatcher.group(1)?.uppercase()
        }

        // 3. Extract Service / Train / Bus Number
        var serviceNumber: String? = null
        val serviceRegex = Pattern.compile("(?i)(?:train\\s*(?:no|#)?|service\\s*(?:no|#)?|bus\\s*(?:no|#)?|trip\\s*(?:no)?)[:\\s#-]*([a-z0-9-]{3,10})")
        val serviceMatcher = serviceRegex.matcher(sanitized)
        if (serviceMatcher.find()) {
            serviceNumber = serviceMatcher.group(1)?.uppercase()
        }

        // 4. Extract Origin / Boarding Point & Destination / Drop-off Point
        var origin: String? = null
        var boardingPoint: String? = null
        var destination: String? = null
        var dropOffPoint: String? = null

        // Try structured labeled patterns first: From: Chennai To: Tirunelveli
        val fromToRegex = Pattern.compile("(?i)(?:from|origin|source|departing\\s*from)[:\\s]+([A-Za-z\\s().,-]{3,35})(?=(?:\\s+to|\\s+dest|\\s+arrival|\\n|$))")
        val fromMatcher = fromToRegex.matcher(sanitized)
        if (fromMatcher.find()) {
            origin = cleanStationName(fromMatcher.group(1))
        }

        val toRegex = Pattern.compile("(?i)(?:to|destination|dest|arriving\\s*at)[:\\s]+([A-Za-z\\s().,-]{3,35})(?=(?:\\s+on|\\s+date|\\s+boarding|\\s+drop|\\n|$))")
        val toMatcher = toRegex.matcher(sanitized)
        if (toMatcher.find()) {
            destination = cleanStationName(toMatcher.group(1))
        }

        // Try boarding point / drop-off specific labels
        val boardingRegex = Pattern.compile("(?i)(?:boarding\\s*(?:point|at)?)[:\\s]+([A-Za-z0-9\\s().,-]{3,35})")
        val boardingMatcher = boardingRegex.matcher(sanitized)
        if (boardingMatcher.find()) {
            boardingPoint = cleanStationName(boardingMatcher.group(1))
        }

        val dropOffRegex = Pattern.compile("(?i)(?:drop\\s*(?:off|point)?)[:\\s]+([A-Za-z0-9\\s().,-]{3,35})")
        val dropOffMatcher = dropOffRegex.matcher(sanitized)
        if (dropOffMatcher.find()) {
            dropOffPoint = cleanStationName(dropOffMatcher.group(1))
        }

        // Hyphenated fallback if from/to not found: e.g. "Chennai Central - Tirunelveli Jn" or "New York -> Boston"
        if (origin == null || destination == null) {
            val hyphenRegex = Pattern.compile("([A-Za-z\\s]{3,30})\\s*(?:-|->|—|to)\\s*([A-Za-z\\s]{3,30})")
            val hyphenMatcher = hyphenRegex.matcher(sanitized)
            if (hyphenMatcher.find()) {
                if (origin == null) origin = cleanStationName(hyphenMatcher.group(1))
                if (destination == null) destination = cleanStationName(hyphenMatcher.group(2))
            }
        }

        // 5. Extract Departure & Scheduled Arrival Times
        var departureTimeStr: String? = null
        var scheduledArrivalTimeStr: String? = null
        var scheduledEpochMs: Long? = null

        // Time regex matching 20:30, 08:30 PM, 06:15
        val timeRegex = Pattern.compile("(?:\\b|\\s)(?:(?:0?[1-9]|1[0-2]):[0-5][0-9]\\s*(?:am|pm)|(?:[01]?[0-9]|2[0-3]):[0-5][0-9])\\b", Pattern.CASE_INSENSITIVE)
        val timeMatcher = timeRegex.matcher(sanitized)
        val extractedTimes = mutableListOf<String>()
        while (timeMatcher.find()) {
            extractedTimes.add(timeMatcher.group().trim())
        }

        if (extractedTimes.isNotEmpty()) {
            departureTimeStr = extractedTimes[0]
            if (extractedTimes.size >= 2) {
                scheduledArrivalTimeStr = extractedTimes[1]
                scheduledEpochMs = parseTimeToTimestamp(scheduledArrivalTimeStr)
            }
        }

        // 6. Calculate Confidence Score & Validate Ambiguities
        var confidence = 0.0f
        if (!destination.isNullOrEmpty()) confidence += 0.35f else ambiguous.add("Destination station/point not detected")
        if (!origin.isNullOrEmpty()) confidence += 0.20f else ambiguous.add("Origin station not detected")
        if (scheduledArrivalTimeStr != null) confidence += 0.20f else ambiguous.add("Scheduled arrival time missing")
        if (pnr != null) confidence += 0.15f
        if (serviceNumber != null) confidence += 0.10f

        val snippet = sanitized.take(160).replace("\n", " ")

        return ExtractedTicket(
            origin = origin,
            destination = destination,
            boardingPoint = boardingPoint,
            dropOffPoint = dropOffPoint,
            transportMode = mode,
            operator = operator,
            serviceNumber = serviceNumber,
            pnr = pnr,
            departureTimeStr = departureTimeStr,
            scheduledArrivalTimeStr = scheduledArrivalTimeStr,
            scheduledArrivalEpochMs = scheduledEpochMs,
            intermediateStops = emptyList(),
            confidenceScore = confidence.coerceIn(0f, 1f),
            rawTextSnippet = snippet,
            ambiguousFields = ambiguous
        )
    }

    private fun cleanStationName(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9\\s().,-]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return if (cleaned.length >= 3) cleaned else null
    }

    private fun parseTimeToTimestamp(timeStr: String?): Long? {
        if (timeStr == null) return null
        try {
            val now = Calendar.getInstance()
            val format = if (timeStr.lowercase().contains("am") || timeStr.lowercase().contains("pm")) {
                SimpleDateFormat("hh:mm a", Locale.getDefault())
            } else {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            }
            val parsedDate = format.parse(timeStr) ?: return null
            val parsedCal = Calendar.getInstance().apply { time = parsedDate }

            now.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
            now.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)

            // If time is earlier today, schedule for next day
            if (now.timeInMillis < System.currentTimeMillis()) {
                now.add(Calendar.DAY_OF_YEAR, 1)
            }
            return now.timeInMillis
        } catch (e: Exception) {
            return null
        }
    }
}
