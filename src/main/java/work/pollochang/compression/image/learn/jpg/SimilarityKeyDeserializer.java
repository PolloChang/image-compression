package work.pollochang.compression.image.learn.jpg;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自訂的 KeyDeserializer，用於將 String 型別的 key 還原成 SimilarityKey 物件。
 */
public class SimilarityKeyDeserializer extends KeyDeserializer {

    // 使用正規表示式來解析 record 的 toString() 格式
    private static final Pattern KEY_PATTERN = Pattern.compile("SimilarityKey\\[widthBucket=(\\d+), heightBucket=(\\d+), sizeBucket=(-?\\d+)\\]");

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        Matcher matcher = KEY_PATTERN.matcher(key);
        if (matcher.matches()) {
            try {
                int widthBucket = Integer.parseInt(matcher.group(1));
                int heightBucket = Integer.parseInt(matcher.group(2));
                long sizeBucket = Long.parseLong(matcher.group(3));
                return new SimilarityKey(widthBucket, heightBucket, sizeBucket);
            } catch (NumberFormatException e) {
                throw new IOException("無法從 key 解析數字: " + key, e);
            }
        }
        // 如果格式不符，拋出例外
        throw new IOException("無法將 key 反序列化為 SimilarityKey: " + key);
    }
}