package com.simiacryptus.cognotik.plan.tools.file

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GenerateQRImageTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateQRImageTaskExecutionConfigData?
) : AbstractFileTask<GenerateQRImageTask.GenerateQRImageTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GenerateQRImageTaskExecutionConfigData(
        @Description("The output image file to be created (relative path, must end with .png, .jpg, or .jpeg)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., reference images, style guides)")
        related_files: List<String>? = null,
        @Description("The data/text content to encode in the QR code")
        val qr_content: String? = null,
        @Description("Artistic style directive for the Image Agent to apply to the QR code (e.g., 'Make it look like a watercolor painting', 'Add a forest background')")
        val style_directive: String? = null,
        @Description("Size of the QR code in pixels (default: 500)")
        val qr_size: Int? = 500,
        @Description("Maximum number of retry attempts if QR verification fails (default: 3)")
        val max_retries: Int? = 3,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateQRImage.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) {
                return "GenerateQRImageTask requires at least one output file to be specified"
            }
            val imageFile = files.first()
            if (!imageFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
                return "GenerateQRImageTask file must have .png, .jpg, or .jpeg extension: $imageFile"
            }
            if (qr_content.isNullOrBlank()) {
                return "GenerateQRImageTask requires qr_content to be specified"
            }
            if (style_directive.isNullOrBlank()) {
                return "GenerateQRImageTask requires style_directive to be specified"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
GenerateQRImage - Generate artistic QR codes using AI image processing
  ** files: The output image file to be created (relative path, must end with .png, .jpg, or .jpeg)
  ** qr_content: The data/text content to encode in the QR code
  ** style_directive: Artistic style directive for the Image Agent (e.g., 'watercolor painting')
  ** qr_size: Size of the QR code in pixels (default: 500)
  ** max_retries: Maximum number of retry attempts if QR verification fails (default: 3)
  ** related_files: Additional files for context (e.g., reference images)
        """.trimIndent()
    }

    override fun toString(relativePath: File): CharSequence? {
        return when (relativePath.name.split('.').last()) {
            "png", "jpg", "jpeg" -> null
            else -> super.toString(relativePath)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val imageFiles = executionConfig?.files ?: emptyList()
        if (imageFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No output file specified")
            return
        }

        val qrContent = executionConfig?.qr_content
        if (qrContent.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No QR content specified")
            return
        }

        val styleDirective = executionConfig?.style_directive
        if (styleDirective.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No style directive specified")
            return
        }

        val qrSize = executionConfig?.qr_size ?: 500
        val maxRetries = executionConfig?.max_retries ?: 3
        val imageOutputFile = imageFiles.first()

        if (!imageOutputFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
            resultFn("CONFIGURATION ERROR: File must have .png, .jpg, or .jpeg extension: $imageOutputFile")
            return
        }

        val tabs = TabbedDisplay(task)
        val overviewTab = tabs.newTask("Overview")
        overviewTab.header("Generating Artistic QR Code: `$imageOutputFile`", level = 2)
        overviewTab.add(MarkdownUtil.renderMarkdown("### QR Content\n```\n$qrContent\n```", ui = task.ui))
        overviewTab.add(MarkdownUtil.renderMarkdown("### Style Directive\n$styleDirective", ui = task.ui))

        try {
            // Step 1: Generate base QR code with high error correction
            val baseQrTab = tabs.newTask("Base QR")
            baseQrTab.header("Step 1: Generating Base QR Code (Error Correction Level H)", level = 3)
            val baseQrImage = generateQRCode(qrContent, qrSize)

            // Display base QR
            baseQrTab.add("Base QR Code:")
            baseQrTab.image(baseQrImage)

            // Verify base QR is readable
            val baseVerification = verifyQRCode(baseQrImage)
            if (baseVerification != qrContent) {
                resultFn("ERROR: Base QR code verification failed")
                return
            }
            baseQrTab.add("✓ Base QR code verified successfully", additionalClasses = "text-success")

            // Load reference images if any
            val inputImageFiles = executionConfig?.related_files?.filter {
                it.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))
            } ?: emptyList()
            val inputImages = inputImageFiles.mapNotNull { filePath ->
                val file = root.resolve(filePath)
                if (file.toFile().exists()) {
                    val image = ImageIO.read(file.toFile())
                    ImageAndText(image = image, text = "Reference image: $filePath")
                } else null
            }

            // Step 2: Process with Image Agent
            val stylingTab = tabs.newTask("Styling")
            stylingTab.header("Step 2: Applying Artistic Style", level = 3)

            val imageAgent = ImageProcessingAgent(
                prompt = """You are an artistic QR code designer. Your task is to stylize a QR code while ensuring it remains scannable.
                    
CRITICAL REQUIREMENTS:
- The QR code MUST remain scannable after your modifications
- Preserve the overall structure and contrast of the QR code
- The three large corner squares (finder patterns) must remain clearly visible
- Maintain sufficient contrast between light and dark modules
- You can add artistic elements, colors, gradients, and decorations
- You can modify the style of the modules (squares) but keep their positions
- Background elements should not obscure the QR pattern""",
                name = "QRCodeArtist",
                model = orchestrationConfig.defaultImage,
            )

            var styledImage: BufferedImage? = null
            var verifiedContent: String? = null
            var attempt = 0

            while (attempt < maxRetries && verifiedContent != qrContent) {
                attempt++
                stylingTab.header("Attempt $attempt of $maxRetries", level = 4)

                val prompt = if (attempt == 1) {
                    """Apply this artistic style to the QR code: $styleDirective
                    
The QR code encodes: "$qrContent"
Ensure the result remains scannable."""
                } else {
                    """Apply this artistic style to the QR code: $styleDirective
                    
The QR code encodes: "$qrContent"
IMPORTANT: Previous attempt failed verification. Please be more conservative with modifications.
- Keep higher contrast between dark and light areas
- Ensure finder patterns (corner squares) are very clear
- Reduce artistic modifications that might interfere with scanning"""
                }

                val result = imageAgent.answer(listOf(ImageAndText(image = baseQrImage, text = prompt)) + inputImages)
                styledImage = result.image

                // Display styled image
                stylingTab.add("Styled QR Code (Attempt $attempt):")
                stylingTab.image(styledImage!!)

                // Step 3: Verify the styled QR code
                stylingTab.add("Verifying Styled QR Code...")
                verifiedContent = try {
                    styledImage?.let { verifyQRCode(it) }
                } catch (e: Exception) {
                    log.warn("QR verification failed on attempt $attempt", e)
                    null
                }

                if (verifiedContent == qrContent) {
                    stylingTab.add("✓ Verification successful! QR code is readable.", additionalClasses = "text-success")
                } else {
                    stylingTab.add(
                        "✗ Verification failed. " +
                                if (verifiedContent == null) "QR code could not be read."
                                else "Content mismatch: got '$verifiedContent'",
                        additionalClasses = "text-danger"
                    )
                }
            }

            if (verifiedContent != qrContent || styledImage == null) {
                stylingTab.add("⚠️ Warning: Final QR code may not be scannable", additionalClasses = "alert alert-warning")
                stylingTab.add("After $maxRetries attempts, the styled QR code could not be verified. Saving the last attempt anyway.")
            }

            // Save the final image
            val outputPath = root.resolve(imageOutputFile)
            outputPath.toFile().parentFile?.mkdirs()

            val format = when {
                imageOutputFile.endsWith(".png", ignoreCase = true) -> "png"
                imageOutputFile.endsWith(".jpg", ignoreCase = true) -> "jpg"
                imageOutputFile.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
                else -> "png"
            }

            ImageIO.write(styledImage ?: baseQrImage, format, outputPath.toFile())

            val verificationStatus =
                if (verifiedContent == qrContent) "verified and scannable" else "may not be scannable"
            val summary =
                "Generated artistic QR code ($verificationStatus) saved to <a href=\"${task.linkTo(imageOutputFile)}\">$imageOutputFile</a>."

            val finalTab = tabs.newTask("Final Result")
            finalTab.header("Final Artistic QR Code", level = 3)
            finalTab.image(styledImage ?: baseQrImage)
            finalTab.add(MarkdownUtil.renderMarkdown(summary, ui = task.ui))

            if (orchestrationConfig.autoFix) {
                task.complete(summary)
                resultFn(summary)
            } else {
                finalTab.add(
                    MarkdownUtil.renderMarkdown(
                        acceptButtonFooter(task.ui) {
                            try {
                                task.complete(summary)
                                resultFn(summary)
                            } catch (e: Exception) {
                                log.error("Error accepting QR image", e)
                                task.error(e)
                                resultFn("ERROR: ${e.message}")
                            }
                        }, ui = task.ui
                    )
                )
            }

        } catch (e: Exception) {
            log.error("Error generating QR image", e)
            task.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun generateQRCode(content: String, size: Int) = MatrixToImageWriter.toBufferedImage(
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size, mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, // Highest error correction (30%)
                EncodeHintType.MARGIN to (size / 35).coerceAtLeast(5),
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    )

    private fun verifyQRCode(image: BufferedImage): String? {
        // Try multiple verification strategies to better match real-world phone scanners
        val strategies = listOf(
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(img) },
            { img: BufferedImage -> verifyWithHybridBinarizer(img) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(thresholdImage(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(img)) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(enhanceContrast(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(enhanceContrast(img)) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(convertToGrayscale(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(convertToGrayscale(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(enhanceContrast(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(sharpenImage(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(sharpenImage(img))) },
            // Scale variations
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 2.0)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 1.5)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 0.5)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 0.75)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(scaleImage(img, 2.0))) },
            // Aggressive preprocessing combinations
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(thresholdImage(enhanceContrast(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(enhanceContrast(convertToGrayscale(img)))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(adaptiveThreshold(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(adaptiveThreshold(convertToGrayscale(img))) },
        )
        for (strategy in strategies) {
            try {
                val result = strategy(image)
                if (result != null) {
                    log.debug("QR verification succeeded with strategy")
                    return result
                }
            } catch (e: Exception) {
                log.trace("QR verification strategy failed", e)
            }
        }
        log.debug("All QR verification strategies failed")
        return null
    }

    private fun verifyWithHybridBinarizer(image: BufferedImage): String? {
        return try {
            val luminanceSource = BufferedImageLuminanceSource(image)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.PURE_BARCODE to false,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun verifyWithGlobalHistogramBinarizer(image: BufferedImage): String? {
        return try {
            val luminanceSource = BufferedImageLuminanceSource(image)
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(luminanceSource))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.PURE_BARCODE to false,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleImage(image: BufferedImage, scale: Double): BufferedImage {
        val newWidth = (image.width * scale).toInt()
        val newHeight = (image.height * scale).toInt()
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
        g2d.dispose()
        return scaled
    }

    private fun convertToGrayscale(image: BufferedImage): BufferedImage {
        val grayscale = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g2d = grayscale.createGraphics()
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        return grayscale
    }

    private fun enhanceContrast(image: BufferedImage): BufferedImage {
        val enhanced = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        // First pass: find min/max values for histogram stretching
        var minVal = 255
        var maxVal = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                minVal = minOf(minVal, luminance)
                maxVal = maxOf(maxVal, luminance)
            }
        }
        val range = (maxVal - minVal).coerceAtLeast(1)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                // Histogram stretching combined with contrast enhancement
                val newR = (((r - minVal) * 255) / range).coerceIn(0, 255)
                val newG = (((g - minVal) * 255) / range).coerceIn(0, 255)
                val newB = (((b - minVal) * 255) / range).coerceIn(0, 255)
                enhanced.setRGB(x, y, (newR shl 16) or (newG shl 8) or newB)
            }
        }
        return enhanced
    }

    private fun sharpenImage(image: BufferedImage): BufferedImage {
        try {
            val kernel = java.awt.image.Kernel(
                3, 3, floatArrayOf(
                    0f, -1f, 0f,
                    -1f, 5f, -1f,
                    0f, -1f, 0f
                )
            )
            val op = java.awt.image.ConvolveOp(kernel, java.awt.image.ConvolveOp.EDGE_NO_OP, null)
            val dest = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            op.filter(image, dest)
            return dest
        } catch (e: Exception) {
            log.warn("Error sharpening image", e)
            return image
        }
    }

    private fun thresholdImage(image: BufferedImage): BufferedImage {
        val grayscale = convertToGrayscale(image)
        val thresholded = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        // Calculate average luminance for adaptive threshold
        var totalLuminance = 0L
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                totalLuminance += grayscale.getRGB(x, y) and 0xFF
            }
        }
        val avgLuminance = (totalLuminance / (grayscale.width * grayscale.height)).toInt()
        // Use a threshold slightly below average to better capture dark modules
        val threshold = (avgLuminance * 0.9).toInt()
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                val luminance = grayscale.getRGB(x, y) and 0xFF
                val newValue = if (luminance < threshold) 0x000000 else 0xFFFFFF
                thresholded.setRGB(x, y, newValue)
            }
        }
        return thresholded
    }

    private fun adaptiveThreshold(image: BufferedImage): BufferedImage {
        // Use local adaptive thresholding for better results with varying lighting/colors
        val grayscale = if (image.type == BufferedImage.TYPE_BYTE_GRAY) image else convertToGrayscale(image)
        val thresholded = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val blockSize = 15 // Size of local neighborhood
        val c = 10 // Constant subtracted from mean
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                // Calculate local mean
                var sum = 0
                var count = 0
                val halfBlock = blockSize / 2
                for (dy in -halfBlock..halfBlock) {
                    for (dx in -halfBlock..halfBlock) {
                        val nx = (x + dx).coerceIn(0, grayscale.width - 1)
                        val ny = (y + dy).coerceIn(0, grayscale.height - 1)
                        sum += grayscale.getRGB(nx, ny) and 0xFF
                        count++
                    }
                }
                val localMean = sum / count
                val threshold = localMean - c
                val luminance = grayscale.getRGB(x, y) and 0xFF
                val newValue = if (luminance < threshold) 0x000000 else 0xFFFFFF
                thresholded.setRGB(x, y, newValue)
            }
        }
        return thresholded
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Save QR Image") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GenerateQRImageTask::class.java)
        val GenerateQRImage = TaskType(
            "GenerateQRImage",
            "Writing",
            GenerateQRImageTask::class.java,
            GenerateQRImageTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate artistic QR codes with AI styling",
            """
              Creates stylized QR codes using AI image processing while maintaining scannability.
              <ul>
                <li>Generates QR codes with high error correction (30% redundancy)</li>
                <li>Applies artistic styles using AI image generation</li>
                <li>Verifies the resulting QR code remains readable</li>
                <li>Retries with more conservative styling if verification fails</li>
              </ul>
            """
        )
    }
}