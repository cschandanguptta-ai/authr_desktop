package com.example.utils

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipFolder(sourceFolder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
            zipFileOrFolder(sourceFolder, sourceFolder, zos)
        }
    }

    private fun zipFileOrFolder(rootFolder: File, source: File, zos: ZipOutputStream) {
        if (source.isDirectory) {
            val files = source.listFiles() ?: return
            for (file in files) {
                zipFileOrFolder(rootFolder, file, zos)
            }
        } else {
            val entryName = source.relativeTo(rootFolder).path
            zos.putNextEntry(ZipEntry(entryName))
            source.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }
}
