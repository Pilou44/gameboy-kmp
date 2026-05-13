package com.wechantloup.gameboykmp.cartridge

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

actual object SaveManager {

    private val fileManager = NSFileManager.defaultManager
    private val directoryUrl = getDirectory()

    actual fun save(name: String, data: IntArray) {
        val filePath = requireNotNull(getFile(name)) { "Can't create file"}
        val bytes = ByteArray(data.size) { (data[it] and 0xFF).toByte() }

        val nsData = bytes.toNSData()
        nsData.writeToFile(filePath, atomically = true)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun load(name: String): IntArray? {
        val filePath = getFile(name) ?: return null

        val loaded: NSData = NSData.dataWithContentsOfFile(filePath) ?: return null

        val opaquePtr: COpaquePointer = loaded.bytes ?: return null

        val intArray = IntArray(loaded.length.toInt())
        val bytePtr: CPointer<ByteVar> = opaquePtr.reinterpret()
        for (i in 0 until intArray.size) {
            intArray[i] = bytePtr[i].toInt() and 0xFF
        }

        return intArray
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getDirectory(): NSURL? {
        val urls = fileManager.URLsForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomains = NSUserDomainMask,
        )
        val appSupportUrl = urls.firstOrNull() as? NSURL

        val dirUrl = appSupportUrl
            ?.URLByAppendingPathComponent("GBEmulator", isDirectory = true)

        val path = dirUrl?.path ?: return null

        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path,
                withIntermediateDirectories = true,  // creates parents too
                attributes = null,
                error = null  // TODO: handle errors properly
            )
        }
        return dirUrl
    }

    private fun getFile(name: String): String? {
        val fileUrl = directoryUrl?.URLByAppendingPathComponent("$name.sav")
        val filePath = fileUrl?.path ?: return null

        return filePath
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.dataWithBytes(
            bytes = pinned.addressOf(0),   // pointer to first element
            length = size.toULong()
        )
    }
}
