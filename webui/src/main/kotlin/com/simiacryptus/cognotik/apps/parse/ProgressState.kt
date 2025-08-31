package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

data class ProgressState(
    private val progressRef: AtomicReference<Double> = AtomicReference(0.0),
    private val maxRef: AtomicReference<Double> = AtomicReference(0.0),
    private val lastUpdateTime: AtomicLong = AtomicLong(0L),
    private val throttleThresholdMs: Long = 1000L, // 1 second threshold
    val onUpdate: MutableList<(ProgressState) -> Unit> = CopyOnWriteArrayList(),
) {

    var progress: Double
        get() = progressRef.get()
        private set(value) = progressRef.set(max(0.0, value))

    var max: Double
        get() = maxRef.get()
        private set(value) = maxRef.set(max(0.0, value))

    fun add(progress: Double, max: Double) {
        require(progress >= 0) { "Progress must be non-negative, got: $progress" }
        require(max >= 0) { "Max must be non-negative, got: $max" }

        synchronized(this) {
            this.progress = this.progress + progress
            this.max = this.max + max
        }
        notifyListenersThrottled()
    }

    fun getPercentage(): Double {
        val currentMax = max
        return if (currentMax > 0) min(100.0, (progress / currentMax) * 100) else 0.0
    }
    private fun notifyListenersThrottled() {
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastUpdateTime.get()
        if (currentTime - lastUpdate >= throttleThresholdMs) {
            if (lastUpdateTime.compareAndSet(lastUpdate, currentTime)) {
                notifyListeners()
            }
        }
    }


    private fun notifyListeners() {
        onUpdate.forEach { listener ->
            try {
                listener(this)
            } catch (e: Exception) {
                log.warn("Error in progress update listener", e)
            }
        }
    }

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(ProgressState::class.java)
        private const val   PROGRESS_BAR_HTML = """
            <style>
                .progress {
                    width: 100%%;
                    background-color: #f0f0f0;
                    border-radius: 5px;
                    margin: 10px 0;
                    overflow: hidden;
                    box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.1);
                }
            
                .progress-bar {
                    height: 20px;
                    background: linear-gradient(90deg, #4CAF50 0%%, #45a049 100%%);
                    border-radius: 5px;
                    transition: width 0.5s ease-in-out;
                    position: relative;
                    overflow: hidden;
                }
            
                .progress-bar::after {
                    content: "";
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: linear-gradient(
                            90deg,
                            transparent,
                            rgba(255, 255, 255, 0.2),
                            transparent
                    );
                    animation: shimmer 2s infinite;
                }
            
                @keyframes shimmer {
                    0%% {
                        transform: translateX(-100%%);
                    }
                    100%% {
                        transform: translateX(100%%);
                    }
                }
            
                .progress-text {
                    text-align: center;
                    margin-top: 5px;
                    font-size: 12px;
                    color: #666;
                }
            </style>
            <div class="progress">
                <div class="progress-bar" role="progressbar" style="width: %s%%" aria-valuenow="%s" aria-valuemin="0"
                     aria-valuemax="100"></div>
            </div>
            <div class="progress-text">%s%% Complete</div>
            """

        fun progressBar(
            task: SessionTask,
        ): ProgressState {
            val stringBuilder = task.add(formatProgressBar(0.0))!!

            return ProgressState(
                progressRef = AtomicReference(0.0),
                maxRef = AtomicReference(0.0),
            ).apply {
                onUpdate += {
                    val percentage = it.getPercentage()
                    stringBuilder.set(formatProgressBar(percentage))
                    task.update()
                }
            }
        }

        private fun formatProgressBar(percentage: Double): String {
            val formattedPercentage = "%.1f".format(percentage)
            return PROGRESS_BAR_HTML.trimIndent().format(
                formattedPercentage, formattedPercentage, formattedPercentage
            )
        }
    }
}