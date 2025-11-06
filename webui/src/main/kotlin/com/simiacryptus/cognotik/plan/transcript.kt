package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.FileOutputStream
import java.text.SimpleDateFormat

fun SessionTask.transcript(name: String = "transcript"): FileOutputStream? {
  val relativePath = "${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())}.md"
  val (link, file) = Pair(linkTo(relativePath), resolveUserFile(relativePath))
  val markdownTranscript = file?.outputStream()
  complete(
    "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
      link.removeSuffix(
        ".md"
      )
    }.pdf' target='_blank'>pdf</a>",
    additionalClasses = "verbose"
  )
  return markdownTranscript
}