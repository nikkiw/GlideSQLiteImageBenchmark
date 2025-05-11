# Glide Performance Testing: SQLite BLOB vs File System

<p >
  <img src="docs/images/SQLiteImageBenchmark.jpg" alt="App Preview" width="240"/>
  <br/>
  <em>Performance testing interface</em>
</p>

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

This project investigates the performance differences between loading images using Glide from SQLite BLOB storage versus the Android file system. The research includes various optimization techniques and their impact on loading times in both single and parallel loading scenarios.

## Key Features

- Multiple image loading strategies comparison (File System, SQLite BLOB, Direct Buffer, Okio)
- Single and parallel loading performance benchmarks
- Detailed performance metrics and analysis
- Memory allocation tracking
- Export results to CSV for further analysis

## Project Structure

```
├── app/                            # Main application module
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/
│               └── com/ndev/sqliteimagebenchmark/
│                   ├── GlideAppModule.kt          # Glide configuration
│                   └── MainActivity.kt            # UI and test runner
├── benchmark/                      # Benchmark module
│   └── src/
│       └── androidTest/
│           └── java/
│               └── com/ndev/benchmark/
│                   ├── GlidePerformanceBenchmark.kt   # Performance benchmarks
│                   └── helper/
│                       └── TestAssetsHelper.kt        # Test utilities
└── benchmarkablelib/              # Core library module
    └── src/
        └── main/
            └── java/
                └── com/ndev/benchmarkablelib/
                    ├── db/                    # Database layer
                    │   └── Entities.kt        # Room entities and DAOs
                    ├── glide/                 # Glide integration
                    │   ├── BlobDataLoader.kt          # Basic BLOB loader
                    │   ├── BlobDataLoaderOkio.kt      # Okio-based loader
                    │   ├── ByteBufferBackedInputStream.kt
                    │   └── SqlImageDataLoader.kt      # SQL optimized loader
                    ├── model/                 # Data models
                    │   ├── BlobData.kt
                    │   ├── PerfModels.kt      # Performance measurement models
                    │   └── SqlImageData.kt
                    ├── performance/           # Performance testing
                    │   └── GlidePerformanceTester.kt
                    └── repository/            # Data access layer
                        └── ImageRepository.kt

```

### Key Components:

1. **Core Library (`benchmarkablelib`)**:
   - Complete image loading infrastructure
   - Multiple optimization strategies implementation
   - Performance measurement utilities

2. **Benchmark Module (`benchmark`)**:
   - Automated performance tests
   - Standardized measurement procedures
   - Comparison of different loading strategies

3. **Demo Application (`app`)**:
   - Interactive testing interface
   - Real-time performance visualization
   - Results export functionality

## How Testing is Conducted

1. **Data Preparation**:
    - Loading test images from app resources
    - Saving each image to SQLite as BLOB
    - Simultaneously saving the same images to disk

2. **Testing Process**:
    - First, disk loading test is conducted
    - Then, SQLite BLOB loading test is conducted
    - For each source, a specified number of iterations is performed
    - Loading time is measured for each image

3. **Results Analysis**:
    - Calculation and display of statistical data (mean, median, min/max, standard deviation)
    - Performance comparison between methods
    - Ability to export results to CSV for further analysis

## Usage Instructions:

1. Launch the application
2. Click "Load Test Data" to prepare test images
3. Specify desired number of iterations (default is 10)
4. Click "Run Tests" to start testing
5. View results on screen
6. If needed, export data to CSV file for deeper analysis

For running automated benchmarks, execute:
```bash
./gradlew :benchmark:connectedReleaseAndroidTest
```

## Technical Requirements

* Android 8.1 (API 27) или выше
* Kotlin 2.1.10 или выше
* Glide 4.15.1
* Room 2.7.1
* Coroutines 1.10.1

## Testing Notes:

- During testing, each image is loaded with disabled memory cache (skipMemoryCache)
- Before testing, Glide cache is cleared for more accurate results
- To reduce the influence of random factors, each test is performed multiple times

## Modification:

To add your own test images:
1. Add your images to the /res/drawable/ folder

## Source Images

Images are taken from [The Oxford-IIIT Pet Dataset](https://www.robots.ox.ac.uk/~vgg/data/pets/)
(license: Creative Commons Attribution-ShareAlike 4.0 International License) and converted
to webp format and scaled (by 0.2, 0.5, 1, 2.0, 4.0 times)
