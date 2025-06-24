# Image Compression Tool 圖片壓縮工具

一個高效的圖片批次壓縮工具，旨在減少圖片檔案大小，同時支援 JPG 和 PNG 格式。本工具特別優化了 JPG 壓縮以達到目標檔案大小，並對 PNG 進行尺寸縮放，同時具備學習快取功能，以提升重複壓縮相同或相似圖片的效率。

## ✨ 功能特色

* **JPG 智慧壓縮**：採用獨特的二分搜尋法優化壓縮品質，可將 JPG 圖片精確壓縮至指定的目標檔案大小上限（預設 1MB）。
* **PNG 尺寸縮放**：根據指定的最大寬高（預設 1920x1920）對 PNG 圖片進行等比例縮放，適用於需要限制圖片尺寸的場景。
* **學習快取機制 (H2 資料庫)**：引入 H2 資料庫作為二級快取，並將快取資料載入記憶體 (L1 快取)。當壓縮相同或相似的圖片時，能重複利用過去學習到的最佳壓縮參數（品質與縮放比例），大幅提升處理效率並減少重複計算。
* **記憶體優化**：透過圖片二次取樣（subsampling）技術，在解碼讀取大型圖片時降低記憶體消耗，有效避免記憶體溢位問題，特別適用於處理高解析度圖片。
* **高效批次處理**：支援從文字檔案中讀取圖片路徑列表，進行批次處理，並利用多執行緒平行處理圖片，以最大化利用 CPU 核心資源，加速壓縮過程。
* **詳細統計報告**：壓縮任務完成後，提供全面的統計報告，包括原始檔案總大小、壓縮後檔案總大小、節省的硬碟空間以及總空間節省百分比，讓使用者清楚了解壓縮效益。
* **無外部依賴**：完全基於 Java 實現，不依賴 JVM 外部的額外工具或服務，便於部署和使用。

## 📋 系統要求

* **Java Development Kit (JDK)**：版本 21 或更高版本。
* **運行環境建議**：
    * **CPU**：4 vCore
    * **RAM**：4 GB
    * **應用場景**：設計用於壓縮 1,000,000 張以上圖片的批次處理任務。

## 🚀 如何使用

### 1. 下載可執行 JAR

請從 [GitHub Releases](https://github.com/pollochang/image-compression/releases) 下載最新版本的 `image-compression-tool-X.X.X.jar` 檔案 (待發布後提供具體連結)。

### 2. 準備圖片列表檔案

創建一個文字檔案，每行包含一張您希望壓縮的圖片的絕對路徑。例如：
`file-list.txt`
```

/data/images/photo1.jpg
/data/images/screenshot.png
/home/user/pictures/vacation/DSC\_0001.JPG
...

````

### 3. 命令列參數說明

本工具使用 `picocli` 庫提供豐富的命令列參數：

```bash
$ java -jar image-compression-tool.jar -h
Usage: image-compressor [-hV] [--cache-db=<h2DbFile>] -f=<fileList>
                        [-i=<minHeight>] -o=<saveDir> [-q=<quality>]
                        [-s=<minSizeBytes>] [-t=<targetMaxSizeBytes>]
                        [--timeOut=<timeOutHr>] [-w=<minWidth>]
批次圖片壓縮工具
      --cache-db=<h2DbFile>
                            H2 學習快取資料庫的檔案路徑 (預設: image-compression-cache)。
  -f, --file-list=<fileList>
                            包含圖片路徑的文字檔案 (必填)。
  -h, --help                顯示幫助訊息並退出。
  -i, --minHeight=<minHeight>
                            PNG 壓縮時限制的最小高度 (預設: 1920)。
  -o, --output-dir=<saveDir>
                            壓縮後圖片的儲存目錄 (必填)。
  -q, --quality=<quality>   JPG 壓縮的初始品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大) (預設: 0.25)。
  -s, --minSize=<minSizeBytes>
                            限制要壓縮的圖片大小，小於此值則跳過壓縮 (預設: 1048576 (1MB))。
  -t, --target-max-size=<targetMaxSizeBytes>
                            JPG 壓縮後單一檔案的目標大小上限(bytes) (預設: 1048576, 即 1MB)。
      --timeOut=<timeOutHr> 設定執行時間超時(小時) (預設: 24 小時)。
  -V, --version             顯示版本資訊並退出。
  -w, --minWidth=<minWidth> PNG 壓縮時限制的最小寬度 (預設: 1920)。
````

### 4\. 執行範例

為了獲得最佳性能，建議為 JVM 配置足夠的記憶體。

**基本執行範例：**

```bash
java -jar image-compression-tool.jar \
    -f /data/tmp/resize-image/file-list.txt \
    -o /data/tmp/resize-image/result \
    -t 524288 \
    --cache-db ./my-compression-cache.db
```

此命令將讀取 `/data/tmp/resize-image/file-list.txt` 中的圖片，將壓縮後的圖片儲存到 `/data/tmp/resize-image/result`，並設定 JPG 目標大小上限為 512KB (524288 Bytes)。快取資料庫檔案將儲存為 `my-compression-cache.db` 在當前目錄。

**Linux Shell 搭配 JVM 最佳化參數：**

```bash
#!/bin/bash
# 設定 Java 環境變數 (請根據您的實際路徑調整)
export JAVA_HOME=/usr/local/lib/jvm/jdk21-latest
export PATH=$PATH:$JAVA_HOME/bin

# 設定 JVM 運行參數，為大量圖片處理預留足夠記憶體
# -Xms 和 -Xmx 設定初始和最大堆記憶體大小
# -XX:+UseParallelGC 使用平行垃圾回收器以提高吞吐量
# -XX:+UseStringDeduplication 啟用字串去重 (Java 8u20+)
# -Xlog:gc... 設定 GC 日誌輸出
export JAVA_OPT="-Xms2560m -Xmx2560m -XX:+UseParallelGC -XX:+UseStringDeduplication -Xlog:gc*:file=./logs/gc.log:time,level,tags:filecount=5,filesize=10m"

# 執行圖片壓縮工具
java ${JAVA_OPT} -jar image-compression-tool.jar \
    -f /data/tmp/resize-image/file-list.txt \
    -o /data/tmp/resize-image/result \
    -q 0.25 \
    --timeOut 1 \
    --cache-db /data/tmp/image-cache/learned_params
```

此腳本將使用 2.5GB 的記憶體處理圖片，初始 JPG 品質為 0.25，最長運行 1 小時，並將 H2 快取資料庫檔案儲存在 `/data/tmp/image-cache/learned_params.mv.db` 和 `/data/tmp/image-cache/learned_params.trace.db`。

### 5\. 日誌檔案

程式運行時會在當前目錄的 `logs/` 子目錄中生成日誌檔案 `image-compression.log`。預設日誌級別為 `info`。您可以在 `src/main/resources/logback.xml` 中修改日誌配置。

## 🛠 開發與貢獻

歡迎任何形式的貢獻！如果您發現 Bug 或有任何功能建議，請隨時在 [GitHub Issues](https://www.google.com/search?q=https://github.com/pollochang/image-compression/issues) 提交。

## 📜 授權

本專案採用 MIT 授權協議。詳情請參閱 [LICENSE](https://www.google.com/search?q=LICENSE) 檔案。

-----

```