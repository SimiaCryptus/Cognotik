package com.simiacryptus.cognotik.docops

import com.simiacryptus.cognotik.docops.exec.DocExecutionContext
import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.exec.DocTaskKindResolver

/**
 * The three (formerly abstract) platform bindings. Hosts implement this instead of subclassing
 * `DocProcessorBase`.
 */
interface DocOpsHost<K : DocTaskKind, S : Any> {
  val taskKinds: DocTaskKindResolver<K>
  fun newExecutionContext(): DocExecutionContext<K, S>
}