package com.sharedash.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.TransferDirection
import com.sharedash.app.model.TransferRecord
import com.sharedash.app.model.TransferStatus
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoButton
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransferHistoryScreen(
    records: List<TransferRecord>,
    onClearAll: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    onOpenDownloadsFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedRecordId by remember { mutableStateOf<String?>(null) }

    val filteredRecords = remember(records, selectedFilter, searchQuery) {
        records.filter { record ->
            val matchesFilter = when (selectedFilter) {
                "RECEIVED" -> record.direction == TransferDirection.RECEIVED
                "SENT" -> record.direction == TransferDirection.SENT
                "COMPLETED" -> record.status == TransferStatus.COMPLETED
                "FAILED" -> record.status == TransferStatus.FAILED || record.status == TransferStatus.CANCELLED
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                record.fileName.contains(searchQuery.trim(), ignoreCase = true) ||
                        record.peerName.contains(searchQuery.trim(), ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    val selectedRecord = remember(selectedRecordId, records) {
        records.find { it.id == selectedRecordId }
    }

    val totalBytesReceived = remember(records) {
        records.filter { it.direction == TransferDirection.RECEIVED && it.status == TransferStatus.COMPLETED }
            .sumOf { it.fileSize }
    }
    val totalBytesSent = remember(records) {
        records.filter { it.direction == TransferDirection.SENT && it.status == TransferStatus.COMPLETED }
            .sumOf { it.fileSize }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = NeoCard,
            title = {
                Text(
                    text = "Clear Transfer History?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove all logs from history. Transferred files on your device will not be deleted.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    }
                ) {
                    Text("Clear All", color = NeoRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  1. TOP BAR
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(colors = listOf(NeoBlue, NeoCyan))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Transfer History",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${records.size} records",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (records.isNotEmpty()) {
                        NeoButton(
                            onClick = { showClearDialog = true },
                            cornerRadius = 10.dp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear History",
                                tint = NeoRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    NeoButton(
                        onClick = onOpenDownloadsFolder,
                        cornerRadius = 10.dp,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Downloads",
                            tint = NeoCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ═══════════════════════════════════════════════════════════════
        //  2. COMPACT STATS ROW
        // ═══════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeoCard(modifier = Modifier.weight(1f), cornerRadius = 14.dp, elevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = NeoGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Received", fontSize = 10.sp, color = TextMuted)
                        Text(text = formatFileSize(totalBytesReceived), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
            NeoCard(modifier = Modifier.weight(1f), cornerRadius = 14.dp, elevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, tint = NeoBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(text = "Sent", fontSize = 10.sp, color = TextMuted)
                        Text(text = formatFileSize(totalBytesSent), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ═══════════════════════════════════════════════════════════════
        //  3. FILTER CHIPS + NARROW SEARCH (same row)
        // ═══════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL" to "All", "RECEIVED" to "↓", "SENT" to "↑", "COMPLETED" to "✓").forEach { (filterKey, label) ->
                    val isSelected = selectedFilter == filterKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Brush.linearGradient(listOf(NeoBlue, NeoCyan))
                                else Brush.linearGradient(listOf(NeoCard, NeoCard))
                            )
                            .clickable { selectedFilter = filterKey }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Narrow search box
            NeoInset(modifier = Modifier.width(130.dp), cornerRadius = 10.dp, backgroundColor = NeoCardPressed) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(text = "Search...", fontSize = 11.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeoCyan
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ═══════════════════════════════════════════════════════════════
        //  4. SELECTED RECORD DETAIL PANEL
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = selectedRecord != null, enter = fadeIn(), exit = fadeOut()) {
            selectedRecord?.let { rec ->
                val isReceived = rec.direction == TransferDirection.RECEIVED
                val formattedDate = remember(rec.timestamp) {
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(rec.timestamp))
                }
                NeoCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    borderColor = if (isReceived) NeoGreen.copy(alpha = 0.4f) else NeoBlue.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.fileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(formatFileSize(rec.fileSize))
                                    append(" · ")
                                    append(rec.peerName)
                                    append(" · ")
                                    append(formattedDate)
                                    append(" · ")
                                    append(rec.transportUsed)
                                    if (rec.speedMbps > 0) append(" · %.1f MB/s".format(rec.speedMbps))
                                },
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeoButton(
                                onClick = { openTransferredFile(context, rec) },
                                cornerRadius = 10.dp,
                                accentColor = if (isReceived) NeoGreen else NeoBlue
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Open", tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp).clickable { selectedRecordId = null }
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        //  5. RECORDS LIST
        // ═══════════════════════════════════════════════════════════════
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(NeoCardPressed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching transfers" else "No Transfer History",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try a different search keyword" else "Files sent or received will appear here automatically",
                        fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    val isSelected = record.id == selectedRecordId
                    CompactTransferRecordRow(
                        record = record,
                        isSelected = isSelected,
                        onClick = { selectedRecordId = if (isSelected) null else record.id },
                        onDelete = {
                            if (selectedRecordId == record.id) selectedRecordId = null
                            onDeleteRecord(record.id)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(85.dp)) }
            }
        }
    }
}

@Composable
private fun CompactTransferRecordRow(
    record: TransferRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileIcon = getFileIcon(record.fileName)
    val isReceived = record.direction == TransferDirection.RECEIVED
    val accentColor = if (isReceived) NeoGreen else NeoBlue
    val formattedDate = remember(record.timestamp) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.timestamp))
    }

    NeoCard(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        cornerRadius = 14.dp,
        elevation = if (isSelected) 6.dp else 3.dp,
        borderColor = if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon (compact)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = fileIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Main info (compact - just name + brief meta)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (isReceived) "↓" else "↑"} ${formatFileSize(record.fileSize)} · $formattedDate",
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete button
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete record",
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp).clickable { onDelete() }
            )
        }
    }
}

private fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov", "webm", "flv" -> Icons.Default.Videocam
        "mp3", "wav", "flac", "ogg", "m4a", "aac" -> Icons.Default.Audiotrack
        "zip", "rar", "7z", "tar", "gz", "bz2" -> Icons.Default.Archive
        "pdf", "doc", "docx", "txt", "rtf", "md", "csv", "xlsx", "pptx" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun openTransferredFile(context: android.content.Context, record: TransferRecord) {
    try {
        val path = record.filePath
        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                    setDataAndType(uri, mime)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }
        // Fallback: Open system Downloads app
        val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Transferred file saved in Downloads/ShareDash", Toast.LENGTH_SHORT).show()
    }
}
