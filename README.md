# Image Compression Tool

An efficient batch image compression tool designed to reduce image file sizes, supporting both JPG and PNG formats. This tool specifically optimizes JPG compression to achieve target file sizes and scales PNG images by dimension. It also features a learning cache mechanism to improve efficiency when repeatedly compressing similar images.

## ✨ Features

* **Smart JPG Compression**: Utilizes a unique binary search algorithm to precisely compress JPG images to a specified maximum target file size (default 1MB).
* **PNG Dimension Scaling**: Scales PNG images proportionally based on specified maximum width and height (default 1920x1920), suitable for scenarios requiring image dimension limitations.
* **Learning Cache Mechanism (H2 Database)**: Incorporates an H2 database as a secondary cache, with cache data loaded into memory (L1 cache). When compressing identical or similar images, it reuses previously learned optimal compression parameters (quality and scaling ratio), significantly boosting processing efficiency and reducing redundant computations.
* **Memory Optimization**: Employs image subsampling technology to reduce memory consumption when decoding and reading large images, effectively preventing out-of-memory errors, especially for high-resolution images.
* **Efficient Batch Processing**: Supports reading a list of image paths from a text file for batch processing and leverages multi-threading to process images in parallel, maximizing CPU core utilization and accelerating the compression process.
* **Detailed Statistics Report**: Upon completion of the compression task, a comprehensive statistical report is provided, including total original file size, total compressed file size, disk space saved, and overall space-saving percentage, allowing users to clearly understand the compression benefits.
* **No External Dependencies**: Implemented entirely in Java, with no reliance on external tools or services beyond the JVM, making it easy to deploy and use.

## 📋 System Requirements

* **Java Development Kit (JDK)**: Version 21 or higher.
* **Recommended Runtime Environment**:
    * **CPU**: 4 vCore
    * **RAM**: 4 GB
    * **Application Scenario**: Designed for batch processing tasks involving 1,000,000 or more images.

## 🚀 How to Use

### 1. Download the Executable JAR

Please download the latest `image-compression-tool-X.X.X.jar` file from [GitHub Releases](https://github.com/pollochang/image-compression/releases) (link will be available after release).

### 2. Prepare the Image List File

Create a text file where each line contains the absolute path to an image you wish to compress. For example:
`file-list.txt`
```

/data/images/photo1.jpg
/data/images/screenshot.png
/home/user/pictures/vacation/DSC\_0001.JPG
...

````

### 3. Command-Line Arguments

This tool uses the `picocli` library to provide comprehensive command-line arguments:

```bash
$ java -jar image-compression-tool.jar -h
Usage: image-compressor [-hV] [--cache-db=<h2DbFile>] -f=<fileList>
                        [-i=<minHeight>] -o=<saveDir> [-q=<quality>]
                        [-s=<minSizeBytes>] [-t=<targetMaxSizeBytes>]
                        [--timeOut=<timeOutHr>] [-w=<minWidth>]
Batch Image Compression Tool
      --cache-db=<h2DbFile>
                            File path for the H2 learned cache database (default: image-compression-cache).
  -f, --file-list=<fileList>
                            Text file containing image paths (required).
  -h, --help                Show this help message and exit.
  -i, --minHeight=<minHeight>
                            Minimum height for PNG compression (default: 1920).
  -o, --output-dir=<saveDir>
                            Output directory for compressed images (required).
  -q, --quality=<quality>   Initial compression quality for JPG, ranging from 0.0 (lowest quality, smallest file) to 1.0 (highest quality, largest file) (default: 0.25).
  -s, --minSize=<minSizeBytes>
                            Minimum size of images to compress; images smaller than this will be skipped (default: 1048576 (1MB)).
  -t, --target-max-size=<targetMaxSizeBytes>
                            Maximum target size (bytes) for a single compressed JPG file (default: 1048576, i.e., 1MB).
      --timeOut=<timeOutHr> Set execution timeout in hours (default: 24 hours).
  -V, --version             Print version information and exit.
  -w, --minWidth=<minWidth> Minimum width for PNG compression (default: 1920).
````

### 4\. Execution Examples

For optimal performance, it is recommended to configure sufficient memory for the JVM.

**Basic Execution Example:**

```bash
java -jar image-compression-tool.jar \
    -f /data/tmp/resize-image/file-list.txt \
    -o /data/tmp/resize-image/result \
    -t 524288 \
    --cache-db ./my-compression-cache.db
```

This command will read images from `/data/tmp/resize-image/file-list.txt`, save compressed images to `/data/tmp/resize-image/result`, and set the maximum target JPG size to 512KB (524288 Bytes). The cache database file will be saved as `my-compression-cache.db` in the current directory.

**Linux Shell with JVM Optimization Parameters:**

```bash
#!/bin/bash
# Set Java environment variables (adjust paths as per your setup)
export JAVA_HOME=/usr/local/lib/jvm/jdk21-latest
export PATH=$PATH:$JAVA_HOME/bin

# Configure JVM runtime parameters to allocate sufficient memory for large image processing
# -Xms and -Xmx set initial and maximum heap memory sizes
# -XX:+UseParallelGC uses a parallel garbage collector for higher throughput
# -XX:+UseStringDeduplication enables string deduplication (Java 8u20+)
# -Xlog:gc... configures GC log output
export JAVA_OPT="-Xms2560m -Xmx2560m -XX:+UseParallelGC -XX:+UseStringDeduplication -Xlog:gc*:file=./logs/gc.log:time,level,tags:filecount=5,filesize=10m"

# Execute the image compression tool
java ${JAVA_OPT} -jar image-compression-tool.jar \
    -f /data/tmp/resize-image/file-list.txt \
    -o /data/tmp/resize-image/result \
    -q 0.25 \
    --timeOut 1 \
    --cache-db /data/tmp/image-cache/learned_params
```

This script will process images using 2.5GB of memory, with an initial JPG quality of 0.25, a maximum runtime of 1 hour, and save the H2 cache database files in `/data/tmp/image-cache/learned_params.mv.db` and `/data/tmp/image-cache/learned_params.trace.db`.

### 5\. Log Files

The program will generate log files in the `logs/` subdirectory of the current working directory, named `image-compression.log`. The default logging level is `info`. You can modify the logging configuration in `src/main/resources/logback.xml`.

## 🛠 Development and Contributions

Contributions of any kind are welcome\! If you find a bug or have any feature suggestions, please feel free to submit them via [GitHub Issues](https://www.google.com/search?q=https://github.com/pollochang/image-compression/issues).

## 📜 License

This project is licensed under the MIT License. See the [LICENSE](https://www.google.com/search?q=LICENSE) file for more details (if you have this file in your project, ensure it's present).

-----
