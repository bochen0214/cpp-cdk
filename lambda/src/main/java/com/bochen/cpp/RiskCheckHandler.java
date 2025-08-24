package com.bochen.cpp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;
import java.util.HashMap;

public class RiskCheckHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> result = new HashMap<>(event);
        double risk = Double.parseDouble(event.get("risk").toString());
        result.put("status", risk >= 50 ? "Reject" : "Accept");
        return result;
    }
}