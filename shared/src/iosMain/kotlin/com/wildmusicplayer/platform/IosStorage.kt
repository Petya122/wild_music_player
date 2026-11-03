package com.wildmusicplayer.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** [KeyValueStore] in NSUserDefaults; [group] prefixes the keys, like separate SharedPreferences files. */
class IosKeyValueStore(private val group: String) : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun k(key: String) = "$group.$key"

    override fun getString(key: String): String? = defaults.stringForKey(k(key))
    override fun putString(key: String, value: String) = defaults.setObject(value, k(key))

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(k(key)) == null) default else defaults.boolForKey(k(key))

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, k(key))

    override fun getStringSet(key: String): Set<String>? =
        defaults.stringArrayForKey(k(key))?.filterIsInstance<String>()?.toSet()

    override fun putStringSet(key: String, value: Set<String>) = defaults.setObject(value.toList(), k(key))

    override fun remove(key: String) = defaults.removeObjectForKey(k(key))
}

/** [FileStore] in the app's Application Support folder (private, backed up, not shown in Files). */
@OptIn(ExperimentalForeignApi::class)
class IosFileStore : FileStore {

    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .first() as String
        "$base/WildMusic".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    override fun readText(name: String): String? =
        NSString.stringWithContentsOfFile("$dir/$name", encoding = NSUTF8StringEncoding, error = null)

    override fun writeText(name: String, text: String) {
        // atomically = true writes a temporary file and renames it, so a crash never leaves half a file
        NSString.create(string = text).writeToFile("$dir/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath("$dir/$name", error = null)
    }
}
