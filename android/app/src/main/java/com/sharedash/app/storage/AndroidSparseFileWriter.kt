package com.sharedash.app.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.sharedash.app.model.FileMeta
import java.io.File
import java.io.RandomAccessFile

class AndroidSparseFileWriter(
    private val context: Context,
    private val filesMeta: List<FileMeta>
) {
    private val baseDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ShareDash"
    )

    private val fileHandles = mutableMapOf<Int, RandomAccessFile>()

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        preallocateFiles()
    }

    private fun preallocateFiles() {
        for (meta in filesMeta) {
            val sanitized = sanitizePath(meta.relativePath)
            val targetFile = File(baseDir, sanitized)

            targetFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            try {
                val raf = RandomAccessFile(targetFile, "rw")
                raf.setLength(meta.sizeBytes)
                fileHandles[meta.fileIndex] = raf
                Log.d(TAG, "Preallocated ${targetFile.absolutePath} with ${meta.sizeBytes} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preallocate file: ${e.message}")
            }
        }
    }

    @Synchronized
    fun writeChunk(fileIndex: Int, offset: Long, data: ByteArray): Boolean {
        val raf = fileHandles[fileIndex] ?: return false
        return try {
            raf.seek(offset)
            raf.write(data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chunk at offset $offset in file $fileIndex: ${e.message}")
            false
        }
    }

    @Synchronized
    fun finalizeFile(fileIndex: Int) {
        val raf = fileHandles.remove(fileIndex)
        try {
            raf?.close()
            val meta = filesMeta.find { it.fileIndex == fileIndex }
            if (meta != null && meta.modifiedTimestamp > 0) {
                val targetFile = File(baseDir, sanitizePath(meta.relativePath))
                targetFile.setLastModified(meta.modifiedTimestamp * 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing file $fileIndex: ${e.message}")
        }
    }

    fun closeAll() {
        fileHandles.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        fileHandles.clear()
    }

    private fun sanitizePath(path: String): String {
        return path.replace("..", "").trimStart('/', '\\')
    }

    companion object {
        private const val TAG = "AndroidSparseWriter"
    }
}
