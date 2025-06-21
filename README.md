# image-compression
圖片壓縮程式(個人玩具)


## 說明

```bash
java -jar image-compression-1.0-SNAPSHOT.jar -f /data/tmp/resize-image/file-list.txt -o /data/tmp/resize-image/result -q 0.25
```

## 使用場境背景技術需求

1. 使用 Java21 開發的圖片壓縮 
2. 不使用虛擬執行序技術 
3. 不使用 JVM 外部資源 
4. 程式應用場景是要壓縮 1000000 張以上圖片
5. 運作環境要求:
   * CPU: 4 vCore
   * RAM: 4 GB
6. 每張壓縮結果的檔案都限制在 1 MB以下
7. 不使用 Thumbnails: 因為無法達到需求
8. 在壓縮完過後統計 原先檔案總大小，壓縮過後檔案總大小，節省多硬碟空間，總共節省百分比

