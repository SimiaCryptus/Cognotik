package cognotik.actions.agent

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

val VirtualFile.toFile: File get() = File(this.path)

