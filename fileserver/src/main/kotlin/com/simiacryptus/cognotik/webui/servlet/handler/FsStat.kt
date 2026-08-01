package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.EtagUtil
import com.simiacryptus.cognotik.webui.servlet.util.MimeTypeResolver
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Stats JSON (nodejs.md §5.3).
 *
 * `mode` is *synthesised* from the type plus the FileAccessControl read-only
 * bit so that stats.isFile()/isDirectory() and `mode & 0o200` behave sanely.
 * dev/ino/nlink/uid/gid/rdev are reported as 0 and documented as unreliable.
 */
object FsStat {
  private const val S_IFREG = 0x8000
  private const val S_IFDIR = 0x4000
  private const val S_IFLNK = 0xA000
  private const val PERM_755 = 493
  private const val PERM_555 = 365
  private const val PERM_644 = 420
  private const val PERM_444 = 292
  private const val PERM_777 = 511

  fun payload(root: File?, file: File, virtual: String, followLinks: Boolean = true): MutableMap<String, Any?> {
    val attrs: BasicFileAttributes = try {
      if (followLinks) Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
      else Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: IOException) {
      throw FsException(FsErrorCode.ENOENT, if (followLinks) "stat" else "lstat", virtual)
    }
    val readOnly = FileAccessControl.isReadOnly(root, file)
    val type = when {
      attrs.isSymbolicLink -> "symlink"
      attrs.isDirectory -> "dir"
      attrs.isRegularFile -> "file"
      else -> "other"
    }
    val mode = when (type) {
      "dir" -> S_IFDIR or (if (readOnly) PERM_555 else PERM_755)
      "symlink" -> S_IFLNK or PERM_777
      else -> S_IFREG or (if (readOnly) PERM_444 else PERM_644)
    }
    val size = attrs.size()
    val map = linkedMapOf<String, Any?>(
      "path" to virtual,
      "type" to type,
      "size" to size,
      "mtimeMs" to attrs.lastModifiedTime().toMillis(),
      "atimeMs" to attrs.lastAccessTime().toMillis(),
      "ctimeMs" to attrs.lastModifiedTime().toMillis(),
      "birthtimeMs" to attrs.creationTime().toMillis(),
      "mode" to mode,
      "readOnly" to readOnly,
      "hidden" to false,
      "dev" to 0,
      "ino" to 0,
      "nlink" to 1,
      "uid" to 0,
      "gid" to 0,
      "rdev" to 0,
      "blksize" to 4096,
      "blocks" to ((size + 511) / 512)
    )
    if (type == "file") {
      map["etag"] = EtagUtil.weakEtag(file)
      map["mimeType"] = MimeTypeResolver.getMimeType(file.name)
    }
    return map
  }

  /** Compact Dirent-style entry used by readdir. */
  fun dirent(root: File?, file: File, name: String, relative: String, includeStat: Boolean): Map<String, Any?> {
    val type = when {
      Files.isSymbolicLink(file.toPath()) -> "symlink"
      file.isDirectory -> "dir"
      file.isFile -> "file"
      else -> "other"
    }
    val map = linkedMapOf<String, Any?>(
      "name" to name,
      "path" to relative,
      "type" to type
    )
    if (includeStat) {
      map["size"] = if (file.isFile) file.length() else 0L
      map["mtimeMs"] = file.lastModified()
      map["readOnly"] = FileAccessControl.isReadOnly(root, file)
      if (file.isFile) map["mimeType"] = MimeTypeResolver.getMimeType(file.name)
    }
    return map
  }
}