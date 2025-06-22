package work.pollochang.compression.image.cache;

import lombok.extern.slf4j.Slf4j;
import work.pollochang.compression.image.learn.LearnedParams;
import work.pollochang.compression.image.learn.jpg.SimilarityKey;

import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H2 二級快取管理器。
 * 負責所有與 H2 資料庫的底層互動，包括連線、資料表初始化、讀取與批次儲存。
 */
@Slf4j
public class H2CacheManager implements AutoCloseable {

    private Connection connection;
    private final String dbPath;

    // 使用 MERGE 陳述式來實現 "upsert" (update or insert) 功能，效率更高。
    private static final String MERGE_SQL = "MERGE INTO LEARNED_PARAMS_CACHE (WIDTH_BUCKET, HEIGHT_BUCKET, SIZE_BUCKET, QUALITY, SCALE) " +
            "KEY(WIDTH_BUCKET, HEIGHT_BUCKET, SIZE_BUCKET) VALUES (?, ?, ?, ?, ?)";

    /**
     * 建構子，初始化 H2 資料庫路徑。
     * @param dbPath H2 資料庫檔案的路徑。
     */
    public H2CacheManager(Path dbPath) {
        // 移除 .mv.db 副檔名 (如果有的話)，因為 JDBC URL 不需要
        String pathStr = dbPath.toAbsolutePath().toString().replace(".mv.db", "");
        this.dbPath = pathStr;
        // 使用 AUTO_SERVER=TRUE 允許多個進程安全地存取同一個資料庫
        String jdbcUrl = String.format("jdbc:h2:%s;AUTO_SERVER=TRUE", pathStr);
        try {
            this.connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            log.info("成功連線至 H2 資料庫: {}", dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("無法建立 H2 資料庫連線: " + jdbcUrl, e);
        }
    }

    /**
     * 初始化資料庫，如果資料表不存在，則建立它。
     */
    public void initSchema() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS LEARNED_PARAMS_CACHE (" +
                "WIDTH_BUCKET INT, " +
                "HEIGHT_BUCKET INT, " +
                "SIZE_BUCKET BIGINT, " +
                "QUALITY FLOAT, " +
                "SCALE DOUBLE, " +
                "PRIMARY KEY (WIDTH_BUCKET, HEIGHT_BUCKET, SIZE_BUCKET)" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
            log.info("H2 資料表 'LEARNED_PARAMS_CACHE' 已確認存在。");
        } catch (SQLException e) {
            throw new RuntimeException("無法初始化 H2 資料庫 Schema", e);
        }
    }

    /**
     * 從 H2 資料庫讀取所有快取紀錄，並載入到一個記憶體 Map 中。
     * @return 包含所有快取資料的 ConcurrentHashMap。
     */
    public Map<SimilarityKey, LearnedParams> loadAllToMap() {
        Map<SimilarityKey, LearnedParams> cache = new ConcurrentHashMap<>();
        String selectSql = "SELECT WIDTH_BUCKET, HEIGHT_BUCKET, SIZE_BUCKET, QUALITY, SCALE FROM LEARNED_PARAMS_CACHE";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            while (rs.next()) {
                SimilarityKey key = new SimilarityKey(
                        rs.getInt("WIDTH_BUCKET"),
                        rs.getInt("HEIGHT_BUCKET"),
                        rs.getLong("SIZE_BUCKET")
                );
                LearnedParams params = new LearnedParams(
                        rs.getFloat("QUALITY"),
                        rs.getDouble("SCALE")
                );
                cache.put(key, params);
            }
        } catch (SQLException e) {
            log.error("從 H2 載入快取時發生錯誤", e);
            // 即使載入失敗，也返回一個空的 map，讓程式可以繼續執行
        }
        log.info("從 H2 資料庫成功載入 {} 筆學習快取紀錄。", cache.size());
        return cache;
    }

    /**
     * 將記憶體中的快取 Map 批次儲存回 H2 資料庫。
     * 使用批次處理 (batch processing) 以獲得最佳效能。
     * @param cache 要儲存的快取 Map。
     */
    public void saveAllFromMap(Map<SimilarityKey, LearnedParams> cache) {
        if (cache == null || cache.isEmpty()) {
            log.info("記憶體快取為空，無需儲存至 H2。");
            return;
        }

        log.info("準備將 {} 筆快取紀錄批次寫入 H2 資料庫...", cache.size());
        int batchSize = 0;
        final int MAX_BATCH_SIZE = 1000; // 每 1000 筆執行一次

        try (PreparedStatement ps = connection.prepareStatement(MERGE_SQL)) {
            // 關閉自動提交，手動管理交易
            connection.setAutoCommit(false);

            for (Map.Entry<SimilarityKey, LearnedParams> entry : cache.entrySet()) {
                SimilarityKey key = entry.getKey();
                LearnedParams params = entry.getValue();

                ps.setInt(1, key.widthBucket());
                ps.setInt(2, key.heightBucket());
                ps.setLong(3, key.sizeBucket());
                ps.setFloat(4, params.quality());
                ps.setDouble(5, params.scale());
                ps.addBatch();
                batchSize++;

                if (batchSize % MAX_BATCH_SIZE == 0) {
                    ps.executeBatch();
                    log.debug("已提交 {} 筆紀錄至 H2...", batchSize);
                }
            }

            // 執行剩餘的批次
            if (batchSize % MAX_BATCH_SIZE != 0) {
                ps.executeBatch();
            }

            connection.commit(); // 提交整個交易
            log.info("成功將 {} 筆紀錄儲存/更新至 H2 資料庫。", batchSize);

        } catch (SQLException e) {
            log.error("批次儲存快取至 H2 時發生錯誤", e);
            try {
                connection.rollback(); // 如果出錯，則回滾交易
                log.warn("H2 交易已回滾。");
            } catch (SQLException ex) {
                log.error("回滾 H2 交易失敗", ex);
            }
        } finally {
            try {
                connection.setAutoCommit(true); // 恢復自動提交模式
            } catch (SQLException e) {
                log.error("無法恢復 H2 連線的自動提交模式", e);
            }
        }
    }

    /**
     * 關閉資料庫連線，釋放資源。
     */
    @Override
    public void close() {
        if (connection != null) {
            try {
                log.info("正在關閉 H2 資料庫連線...");
                connection.close();
                log.info("H2 資料庫連線已關閉。");
            } catch (SQLException e) {
                log.error("關閉 H2 資料庫連線時發生錯誤。", e);
            }
        }
    }
}