private val demotedDatabases = ConcurrentHashMap.newKeySet<String>()
/**
* Memoized [resolveStoragePath] results keyed by "dbName|root". The probe does
* mkdirs + create/write/delete of a file, and used to run on *every* borrowed
* connection (i.e. every Exposed transaction).
*/
private val resolvedStoragePaths = ConcurrentHashMap<String, String>()
private fun isMemoryBacked(db: String): Boolean =
registeredDatabases[db]?.startsWith("mem:") ?: true