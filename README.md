# image-compression
圖片壓縮程式(個人玩具)


## 說明

參數說明

```bash
$ java -jar image-compression-1.0-SNAPSHOT.jar -h                                                                       
Usage: image-compressor [-hV] -f=<fileList> [-i=<minHeight>] -o=<saveDir>
                        [-q=<quality>] [-s=<minSizeBytes>]
                        [-t=<targetMaxSizeBytes>] [-w=<minWidth>]
批次圖片壓縮工具
  -f, --file-list=<fileList>
                            包含圖片路徑的文字檔案。
  -h, --help                Show this help message and exit.
  -i, --minHeight=<minHeight>
                            限制要壓縮的圖片高 (預設: 1920)。
  -o, --output-dir=<saveDir>
                            壓縮後圖片的儲存目錄。
  -q, --quality=<quality>   壓縮品質，範圍從 0.0 (最低品質，檔案最小) 到 1.0 (最高品質，檔案最大) (預設: 0.25)。
  -s, --minSize=<minSizeBytes>
                            限制要壓縮的圖片大小 (預設: 1048576 (1MB))。
  -t, --target-max-size=<targetMaxSizeBytes>
                            壓縮後單一檔案的目標大小上限(bytes) (預設: 1048576,即 1MB)。
  -V, --version             Print version information and exit.
  -w, --minWidth=<minWidth> 限制要壓縮的圖片長 (預設: 1920)。
```

執行範例

```bash
java -jar image-compression-1.0-SNAPSHOT.jar -f /data/tmp/resize-image/file-list.txt -o /data/tmp/resize-image/result -q 0.25
```


## 使用場境背景技術需求

1. 使用 Java21 開發的圖片壓縮 
2. 不使用 JVM 外部資源 
3. 程式應用場景是要壓縮 1000000 張以上圖片
4. 運作環境要求:
   * CPU: 4 vCore
   * RAM: 4 GB
5. 每張壓縮結果的檔案都限制在 1 MB以下
6. 不使用 Thumbnails: 因為無法達到需求
7. 在壓縮完過後統計 原先檔案總大小，壓縮過後檔案總大小，節省多硬碟空間，總共節省百分比

