package com.ndev.sqliteimagebenchmark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ndev.benchmarkablelib.model.PerformanceResult
import com.ndev.benchmarkablelib.model.PerformanceStatistics
import com.ndev.benchmarkablelib.performance.GlidePerformanceTester
import com.ndev.benchmarkablelib.repository.ImageRepository
import com.ndev.sqliteimagebenchmark.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ImageRepository
    private lateinit var performanceTester: GlidePerformanceTester

    private val testImages = mutableListOf<Triple<Long, String, File>>()
    private var blobResults = listOf<PerformanceResult>()
    private var fileResults = listOf<PerformanceResult>()
    private var lastCsvFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = ImageRepository(this)
        performanceTester = GlidePerformanceTester(this, repository)

        setupListeners()
    }

    private fun setupListeners() {
        binding.loadDataButton.setOnClickListener { loadTestData() }
        binding.startTestButton.setOnClickListener { runPerformanceTests() }
        binding.exportResultsButton.setOnClickListener { exportResultsToCsv() }
        // New share button listener
        binding.shareResultsButton.setOnClickListener { shareResults() }
    }

    private fun loadTestData() {
        lifecycleScope.launch {
            try {
                binding.testStatusTextView.text = "Loading test data..."
                binding.loadDataButton.isEnabled = false
                repository.clearAllImages()
                val imageResIds = R.raw::class.java.fields.mapNotNull { field ->
                    try {
                        field.getInt(null)
                    } catch (e: Exception) {
                        null
                    }
                }
                testImages.clear()
                testImages.addAll(repository.loadTestImages(imageResIds.subList(0,20)))
                binding.testStatusTextView.text = "Data loaded. Ready for testing."
                binding.startTestButton.isEnabled = true
            } catch (e: Exception) {
                binding.testStatusTextView.text = "Error loading data: ${e.message}"
                binding.loadDataButton.isEnabled = true
                e.printStackTrace()
            }
        }
    }

    private fun runPerformanceTests() {
        if (testImages.isEmpty()) {
            Toast.makeText(this, "Please load test data first", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                binding.startTestButton.isEnabled = false
                binding.loadDataButton.isEnabled = false
                binding.testStatusTextView.text = "Running performance tests..."

                binding.testStatusTextView.text = "Testing load from SQLite BLOB..."
                blobResults = performanceTester.testBlobLoadingInUI(
                    testImages.map { it.first },
                    testImages.map { it.second },
                    binding.imageView
                )

                binding.testStatusTextView.text = "Testing load from file system..."
                fileResults = performanceTester.testFileLoadingInUI(
                    testImages.map { it.third }, binding.imageView
                )

                displayResults()
                binding.testStatusTextView.text = "Testing completed"
                binding.startTestButton.isEnabled = true
                binding.loadDataButton.isEnabled = true
                binding.exportResultsButton.visibility = View.VISIBLE
                binding.shareResultsButton.visibility = View.VISIBLE

            } catch (e: Exception) {
                binding.testStatusTextView.text = "Testing error: ${e.message}"
                binding.startTestButton.isEnabled = true
                binding.loadDataButton.isEnabled = true
                e.printStackTrace()
            }
        }
    }

    private fun displayResults() {
        if (blobResults.isEmpty() || fileResults.isEmpty()) return
        val blobStats = PerformanceStatistics(blobResults)
        val fileStats = PerformanceStatistics(fileResults)

        binding.blobAvgTimeTextView.text = formatDouble(blobStats.getAverageLoadTime())
        binding.fileAvgTimeTextView.text = formatDouble(fileStats.getAverageLoadTime())
        binding.blobMedianTimeTextView.text = formatDouble(blobStats.getMedianLoadTime())
        binding.fileMedianTimeTextView.text = formatDouble(fileStats.getMedianLoadTime())
        binding.blobMinTimeTextView.text = formatDouble(blobStats.getMinLoadTime())
        binding.fileMinTimeTextView.text = formatDouble(fileStats.getMinLoadTime())
        binding.blobMaxTimeTextView.text = formatDouble(blobStats.getMaxLoadTime())
        binding.fileMaxTimeTextView.text = formatDouble(fileStats.getMaxLoadTime())
        binding.blobStdDevTextView.text = formatDouble(blobStats.getStandardDeviation())
        binding.fileStdDevTextView.text = formatDouble(fileStats.getStandardDeviation())
        binding.blobSuccessRateTextView.text = formatDouble(blobStats.getSuccessRate()) + "%"
        binding.fileSuccessRateTextView.text = formatDouble(fileStats.getSuccessRate()) + "%"

        val blobAvg = blobStats.getAverageLoadTime()
        val fileAvg = fileStats.getAverageLoadTime()
        val difference = ((fileAvg - blobAvg) / fileAvg * 100).roundToInt()
        val summary = when {
            blobAvg < fileAvg -> "SQLite BLOB is ~$difference% faster than file system"
            fileAvg < blobAvg -> "File system is ~${-difference}% faster than SQLite BLOB"
            else -> "SQLite BLOB and file system performance are about the same"
        }
        binding.resultSummaryTextView.text = summary
        binding.resultSummaryTextView.visibility = View.VISIBLE
    }

    private fun exportResultsToCsv() {
        if (blobResults.isEmpty() && fileResults.isEmpty()) return
        lifecycleScope.launch {
            try {
                val results = blobResults + fileResults
                val csv = buildCsvContent(results)
                val file = saveCsvToFile(csv)
                lastCsvFile = file
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Results exported to ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Export error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                e.printStackTrace()
            }
        }
    }

    // New: share the summary text or CSV file
    private fun shareResults() {
        val csvFile = lastCsvFile
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            if (csvFile != null && csvFile.exists()) {
                val uri: Uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${applicationContext.packageName}.fileprovider",
                    csvFile
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, binding.resultSummaryTextView.text.toString())
                type = "text/plain"
            }
        }
        startActivity(Intent.createChooser(shareIntent, "Share test results"))
    }

    private fun buildCsvContent(results: List<PerformanceResult>): String {
        val sb = StringBuilder().apply {
            appendLine("source_type,file_name,file_size,load_time_ms,iteration,success")
            results.forEach { r ->
                appendLine(
                    "${r.sourceType},${r.fileName},${r.fileSize},${r.loadTimeMs},${r.iteration},${r.success}"
                )
            }
        }
        return sb.toString()
    }

    private suspend fun saveCsvToFile(content: String): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "glide_performance_test_$timestamp.csv"
        val file = File(getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { it.write(content.toByteArray()) }
        file
    }

    private fun formatDouble(value: Double): String = String.format("%.2f", value)
    private fun String.toIntOrDefault(default: Int): Int = try {
        toInt()
    } catch (e: NumberFormatException) {
        default
    }
}
