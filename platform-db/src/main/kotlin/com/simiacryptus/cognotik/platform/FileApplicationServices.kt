package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.service.ApplicationServices
import java.io.File

/** Legacy root-scoped service set; now a thin forwarder onto [ApplicationServicesImpl]. */
@Deprecated("Use ApplicationServicesImpl.fileApplicationServices(root)")
open class FileApplicationServices(override val rootDir: File) :
  ApplicationServices by ApplicationServicesImpl.forRoot(rootDir)