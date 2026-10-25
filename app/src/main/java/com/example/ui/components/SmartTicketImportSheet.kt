package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtractedTicket
import com.example.data.model.JourneyEntity
import com.example.data.repository.RoutingRepository
import com.example.data.ticket.TicketImportManager
import com.example.ui.theme.NocturneTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTicketImportSheet(
    activeJourney: JourneyEntity?,
    onDismiss: () -> Unit,
    onConfirmTicket: (
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        wakeUpBufferMinutes: Int,
        transportMode: String,
        pnr: String?,
        scheduledArrivalMs: Long?,
        originName: String?
    ) -> Unit,
    initialSharedText: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = NocturneTheme.colors
    val clipboardManager = LocalClipboardManager.current

    var isProcessing by remember { mutableStateOf(false) }
    var extractedTicket by remember { mutableStateOf<ExtractedTicket?>(null) }
    var rawInputText by remember { mutableStateOf(initialSharedText ?: "") }
    var showOverwriteDialog by remember { mutableStateOf(false) }

    // Editable Form Fields
    var editedDestination by remember { mutableStateOf("") }
    var editedOrigin by remember { mutableStateOf("") }
    var editedMode by remember { mutableStateOf("BUS") }
    var editedPnr by remember { mutableStateOf("") }
    var editedScheduledTime by remember { mutableStateOf("") }
    var editedBufferMinutes by remember { mutableIntStateOf(30) }

    // Automatically parse if shared text was provided
    remember(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            val res = TicketImportManager.extractFromText(initialSharedText)
            extractedTicket = res
            editedDestination = res.destination ?: ""
            editedOrigin = res.origin ?: ""
            editedMode = res.transportMode
            editedPnr = res.pnr ?: ""
            editedScheduledTime = res.scheduledArrivalTimeStr ?: ""
        }
    }

    // PDF Document Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                try {
                    val result = TicketImportManager.extractFromPdf(context, uri)
                    extractedTicket = result
                    editedDestination = result.destination ?: ""
                    editedOrigin = result.origin ?: ""
                    editedMode = result.transportMode
                    editedPnr = result.pnr ?: ""
                    editedScheduledTime = result.scheduledArrivalTimeStr ?: ""
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Image Picker (Zero-permission photo picker)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                try {
                    val result = TicketImportManager.extractFromImage(context, uri)
                    extractedTicket = result
                    editedDestination = result.destination ?: ""
                    editedOrigin = result.origin ?: ""
                    editedMode = result.transportMode
                    editedPnr = result.pnr ?: ""
                    editedScheduledTime = result.scheduledArrivalTimeStr ?: ""
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("smart_ticket_import_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SMART TICKET IMPORT",
                        color = theme.ink,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "LOCAL-FIRST EXTRACTION & ARRIVAL CALIBRATION",
                        color = theme.muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = theme.ink)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            TricolorStripe()
            Spacer(modifier = Modifier.height(14.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = theme.warn, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "PARSING TICKET ON-DEVICE...",
                            color = theme.ink,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Extracting timetable, stations & booking reference locally",
                            color = theme.muted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else if (extractedTicket == null) {
                // Source Selector Screen
                Text(
                    text = "SELECT TICKET SOURCE:",
                    color = theme.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PDF Import
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(theme.navyDeep, RoundedCornerShape(3.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(3.dp))
                            .clickable { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                            .padding(14.dp)
                            .testTag("import_pdf_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = theme.warn, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "PDF E-TICKET", color = theme.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(text = "IRCTC, Bus, Rail", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Image / Screenshot Import
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(theme.navyDeep, RoundedCornerShape(3.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(3.dp))
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(14.dp)
                            .testTag("import_image_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = theme.okText, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "IMAGE / OCR", color = theme.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(text = "Photo, Screenshot", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Paste or Share Text Input
                Text(
                    text = "OR PASTE TICKET TEXT / SMS:",
                    color = theme.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Paste reservation SMS, WhatsApp booking, or PNR confirmation...", color = theme.muted, fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("ticket_raw_text_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.warn,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.ink,
                        unfocusedTextColor = theme.ink
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TactileButton(
                        text = "PASTE CLIPBOARD",
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                rawInputText = clip
                            } else {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.Default.ContentPaste,
                        isSecondary = true,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    TactileButton(
                        text = "PARSE TEXT",
                        onClick = {
                            if (rawInputText.isNotBlank()) {
                                val result = TicketImportManager.extractFromText(rawInputText)
                                extractedTicket = result
                                editedDestination = result.destination ?: ""
                                editedOrigin = result.origin ?: ""
                                editedMode = result.transportMode
                                editedPnr = result.pnr ?: ""
                                editedScheduledTime = result.scheduledArrivalTimeStr ?: ""
                            } else {
                                Toast.makeText(context, "Please enter ticket text", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.Default.Check,
                        testTag = "parse_ticket_text_button",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sample Demo Templates
                Text(
                    text = "QUICK DEMO TEMPLATES:",
                    color = theme.muted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoTemplateChip(label = "IRCTC Train", onClick = {
                        rawInputText = "PNR: 452-9182341 Train: 12631 Nellai Superfast Express\nFrom: Chennai Central (MAS) To: Tirunelveli Junction (TEN)\nDeparture: 20:10 Scheduled Arrival: 06:40 Berth: S3-45"
                    })
                    DemoTemplateChip(label = "Intercity Bus", onClick = {
                        rawInputText = "KPN Travels Ticket #KP-99214\nOrigin: Bangalore City (SBC) Destination: Madurai Junction\nBoarding: Madiwala 22:15 Scheduled Drop: 05:30 AC Sleeper"
                    })
                }
            } else {
                // Editable Review Screen
                val ticket = extractedTicket!!

                // Extraction Confidence Badge
                val confPercent = (ticket.confidenceScore * 100).toInt()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (confPercent >= 70) Color(0xFF1B382B) else Color(0xFF332A15),
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (confPercent >= 70) Color(0xFF4CAF50) else theme.warn,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (confPercent >= 70) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (confPercent >= 70) Color(0xFF81C784) else theme.warn,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$confPercent% CONFIDENCE EXTRACTION",
                                color = if (confPercent >= 70) Color(0xFF81C784) else theme.warnText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!ticket.operator.isNullOrEmpty()) {
                            Text(
                                text = ticket.operator,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (ticket.ambiguousFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "REVIEW REQUIRED: ${ticket.ambiguousFields.joinToString(", ")}",
                        color = theme.warnText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Destination Field
                Text(text = "DESTINATION STATION / DROP-OFF:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = editedDestination,
                    onValueChange = { editedDestination = it },
                    placeholder = { Text("e.g. Tirunelveli Junction", color = theme.muted) },
                    modifier = Modifier.fillMaxWidth().testTag("ticket_edit_destination"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.warn,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.ink,
                        unfocusedTextColor = theme.ink
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Editable Origin Field
                Text(text = "ORIGIN / BOARDING POINT:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = editedOrigin,
                    onValueChange = { editedOrigin = it },
                    placeholder = { Text("e.g. Chennai Central", color = theme.muted) },
                    modifier = Modifier.fillMaxWidth().testTag("ticket_edit_origin"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.warn,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.ink,
                        unfocusedTextColor = theme.ink
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Transport Mode Selector
                Text(text = "CONFIRMED TRANSPORT MODE:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("BUS" to "Intercity Bus", "TRAIN" to "Rail Corridor", "CAR" to "Car / Shared")
                    modes.forEach { (mKey, mLabel) ->
                        val isSel = editedMode.equals(mKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSel) theme.navyDeep else theme.bg, RoundedCornerShape(2.dp))
                                .border(1.dp, if (isSel) theme.warn else theme.border, RoundedCornerShape(2.dp))
                                .clickable { editedMode = mKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mLabel,
                                color = if (isSel) Color.White else theme.ink,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PNR & Scheduled Arrival Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "PNR / TICKET #:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        OutlinedTextField(
                            value = editedPnr,
                            onValueChange = { editedPnr = it },
                            placeholder = { Text("Optional", color = theme.muted) },
                            modifier = Modifier.fillMaxWidth().testTag("ticket_edit_pnr"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.warn,
                                unfocusedBorderColor = theme.border,
                                focusedTextColor = theme.ink,
                                unfocusedTextColor = theme.ink
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "SCHEDULED ARRIVAL:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        OutlinedTextField(
                            value = editedScheduledTime,
                            onValueChange = { editedScheduledTime = it },
                            placeholder = { Text("e.g. 06:40", color = theme.muted) },
                            modifier = Modifier.fillMaxWidth().testTag("ticket_edit_arrival"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.warn,
                                unfocusedBorderColor = theme.border,
                                focusedTextColor = theme.ink,
                                unfocusedTextColor = theme.ink
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wake-Up Buffer Selector
                Text(text = "WAKE-UP ALARM BUFFER:", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        val isSel = editedBufferMinutes == mins
                        Box(
                            modifier = Modifier
                                .background(if (isSel) theme.navyDeep else theme.bg, RoundedCornerShape(2.dp))
                                .border(1.dp, if (isSel) theme.warn else theme.border, RoundedCornerShape(2.dp))
                                .clickable { editedBufferMinutes = mins }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${mins}M",
                                color = if (isSel) theme.warnText else theme.ink,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Confirm Import Action
                TactileButton(
                    text = "CONFIRM & INITIALIZE JOURNEY",
                    onClick = {
                        if (editedDestination.isBlank()) {
                            Toast.makeText(context, "Destination required", Toast.LENGTH_SHORT).show()
                            return@TactileButton
                        }

                        if (activeJourney != null) {
                            showOverwriteDialog = true
                        } else {
                            executeConfirmation(
                                editedDestination = editedDestination,
                                editedOrigin = editedOrigin,
                                editedMode = editedMode,
                                editedPnr = editedPnr,
                                editedBufferMinutes = editedBufferMinutes,
                                scheduledEpochMs = ticket.scheduledArrivalEpochMs,
                                onConfirmTicket = onConfirmTicket
                            )
                        }
                    },
                    icon = Icons.Default.Check,
                    testTag = "confirm_ticket_import_button",
                    minHeight = 50.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                TactileButton(
                    text = "BACK TO SCANNER",
                    onClick = { extractedTicket = null },
                    isSecondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Overwrite Confirmation Dialog
    if (showOverwriteDialog && activeJourney != null) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = {
                Text(
                    text = "REPLACE ACTIVE JOURNEY?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    text = "An active surveillance journey to '${activeJourney.destinationName}' is currently armed. Initializing this imported ticket will terminate current tracking and replace wake-up alarms.",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TactileButton(
                    text = "REPLACE ACTIVE JOURNEY",
                    onClick = {
                        showOverwriteDialog = false
                        executeConfirmation(
                            editedDestination = editedDestination,
                            editedOrigin = editedOrigin,
                            editedMode = editedMode,
                            editedPnr = editedPnr,
                            editedBufferMinutes = editedBufferMinutes,
                            scheduledEpochMs = extractedTicket?.scheduledArrivalEpochMs,
                            onConfirmTicket = onConfirmTicket
                        )
                    },
                    isDanger = true
                )
            },
            dismissButton = {
                TactileButton(
                    text = "KEEP CURRENT JOURNEY",
                    onClick = { showOverwriteDialog = false },
                    isSecondary = true
                )
            }
        )
    }
}

private fun executeConfirmation(
    editedDestination: String,
    editedOrigin: String,
    editedMode: String,
    editedPnr: String,
    editedBufferMinutes: Int,
    scheduledEpochMs: Long?,
    onConfirmTicket: (
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        wakeUpBufferMinutes: Int,
        transportMode: String,
        pnr: String?,
        scheduledArrivalMs: Long?,
        originName: String?
    ) -> Unit
) {
    // Resolve coordinates from built-in preset station catalog if match found, else default
    val lower = editedDestination.lowercase()
    val matched = RoutingRepository.PRESET_TRANSIT_STATIONS.firstOrNull {
        it.name.lowercase().contains(lower) || lower.contains(it.name.lowercase())
    }

    val destLat = matched?.lat ?: 8.7290 // Tirunelveli fallback
    val destLon = matched?.lon ?: 77.7126

    onConfirmTicket(
        editedDestination,
        destLat,
        destLon,
        editedBufferMinutes,
        editedMode.uppercase(),
        editedPnr.ifBlank { null },
        scheduledEpochMs,
        editedOrigin.ifBlank { null }
    )
}

@Composable
private fun DemoTemplateChip(label: String, onClick: () -> Unit) {
    val theme = NocturneTheme.colors
    Box(
        modifier = Modifier
            .background(theme.bg, RoundedCornerShape(2.dp))
            .border(1.dp, theme.border, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = theme.navy, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
