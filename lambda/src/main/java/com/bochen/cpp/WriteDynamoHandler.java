package com.bochen.cpp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WriteDynamoHandler implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("📥 Received event: " + event);

        // 1. 获取 DynamoDB 表名
        String tableName = System.getenv("PRICE_TABLE");
        if (tableName == null || tableName.isEmpty()) {
            context.getLogger().log("❌ Environment variable PRICE_TABLE is missing");
            return "Error: PRICE_TABLE env missing";
        }

        // 2. 获取字段，安全解析
        String timestamp = getStringOrDefault(event, "timestamp", Instant.now().toString());
        String vendor = getStringOrDefault(event, "vendor", "UNKNOWN");
        double price = getDoubleOrDefault(event, "price", 0.0);
        double discount = getDoubleOrDefault(event, "discount", 0.0);
        double risk = getDoubleOrDefault(event, "risk", 0.0);
        String status = getStringOrDefault(event, "status", (risk > 50) ? "Reject" : "Accept");

        // 3. 组装 DynamoDB Item
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("timestamp", AttributeValue.fromS(timestamp));
        item.put("vendor", AttributeValue.fromS(vendor));
        item.put("price", AttributeValue.fromN(String.valueOf(price)));
        item.put("discount", AttributeValue.fromN(String.valueOf(discount)));
        item.put("risk", AttributeValue.fromN(String.valueOf(risk)));
        item.put("status", AttributeValue.fromS(status));

        // 4. 写入 DynamoDB
        try (DynamoDbClient dbClient = DynamoDbClient.create()) {
            dbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());
            context.getLogger().log("✅ Successfully wrote item to DynamoDB: " + item);
        } catch (Exception e) {
            context.getLogger().log("❌ Failed to write item: " + e.getMessage());
            return "Error: " + e.getMessage();
        }

        return "OK";
    }

    /** 安全获取 String 字段，支持默认值 */
    private String getStringOrDefault(Map<String, Object> event, String key, String defaultValue) {
        Object value = event.get(key);
        return (value != null) ? value.toString() : defaultValue;
    }

    /** 安全解析 Double，支持默认值 */
    private double getDoubleOrDefault(Map<String, Object> event, String key, double defaultValue) {
        Object value = event.get(key);
        try {
            return (value != null) ? Double.parseDouble(value.toString()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}