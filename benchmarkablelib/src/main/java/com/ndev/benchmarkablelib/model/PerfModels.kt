package com.ndev.benchmarkablelib.model

data class PerformanceResult(
    val sourceType: String, // "FILE" or "BLOB"
    val fileName: String,
    val fileSize: Long, // size in bytes
    val loadTimeMs: Double, // loading time in milliseconds
    val iteration: Int, // iteration number
    val success: Boolean // whether loading was successful
)

// Class for statistics on results
class PerformanceStatistics(private val results: List<PerformanceResult>) {

    fun getAverageLoadTime(): Double {
        return results.filter { it.success }.map { it.loadTimeMs }.average()
    }

    fun getMedianLoadTime(): Double {
        val sortedTimes = results.filter { it.success }.map { it.loadTimeMs }.sorted()
        if (sortedTimes.isEmpty()) return 0.0

        val middle = sortedTimes.size / 2
        return if (sortedTimes.size % 2 == 0) {
            (sortedTimes[middle - 1] + sortedTimes[middle]) / 2
        } else {
            sortedTimes[middle]
        }
    }

    fun getMinLoadTime(): Double {
        return results.filter { it.success }.minOfOrNull { it.loadTimeMs } ?: 0.0
    }

    fun getMaxLoadTime(): Double {
        return results.filter { it.success }.maxOfOrNull { it.loadTimeMs } ?: 0.0
    }

    fun getStandardDeviation(): Double {
        val times = results.filter { it.success }.map { it.loadTimeMs }
        if (times.isEmpty()) return 0.0

        val avg = times.average()
        val variance = times.map { (it - avg) * (it - avg) }.average()
        return kotlin.math.sqrt(variance)
    }

    fun getSuccessRate(): Double {
        if (results.isEmpty()) return 0.0
        return results.count { it.success } / results.size.toDouble() * 100
    }

    fun getStatsByImageAndSourceType(): Map<Pair<String, String>, PerformanceStatistics> {
        return results.groupBy { Pair(it.fileName, it.sourceType) }
            .mapValues { PerformanceStatistics(it.value) }
    }

    fun getStatsBySourceType(): Map<String, PerformanceStatistics> {
        return results.groupBy { it.sourceType }
            .mapValues { PerformanceStatistics(it.value) }
    }
}
