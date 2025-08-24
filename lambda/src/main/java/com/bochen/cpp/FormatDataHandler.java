package com.bochen.cpp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;
import java.util.HashMap;

public class FormatDataHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("📥 Received event: " + event);

        Map<String, Object> result = new HashMap<>(event);

        // 1. 安全解析 risk
        double risk = 0.0;
        try {
            if (event.containsKey("risk") && event.get("risk") != null) {
                risk = Double.parseDouble(event.get("risk").toString());
            } else {
                context.getLogger().log("⚠️ No risk provided, defaulting to 0");
            }
        } catch (NumberFormatException e) {
            context.getLogger().log("❌ Invalid risk format: " + event.get("risk"));
        }

        // 2. 转换为百分比
        double riskPercentage = risk * 100;
        result.put("risk", riskPercentage);

        context.getLogger().log("✅ Formatted data: " + result);

        // 3. 返回给下一个 Lambda
        return result;
    }
}