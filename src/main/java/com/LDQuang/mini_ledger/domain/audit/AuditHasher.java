package com.LDQuang.mini_ledger.domain.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
class AuditHasher {
    private final ObjectMapper mapper;

    String hash(Object payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(mapper.valueToTree(payload)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash audit record", exception);
        }
    }

    // JSONB reorders object keys and normalizes numeric representations.
    private String canonical(JsonNode node) throws Exception {
        if (node.isObject()) {
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            var fields = new ArrayList<String>();
            for (String name : names) fields.add(mapper.writeValueAsString(name) + ":" + canonical(node.get(name)));
            return "{" + String.join(",", fields) + "}";
        }
        if (node.isArray()) {
            var values = new ArrayList<String>();
            for (JsonNode value : node) values.add(canonical(value));
            return "[" + String.join(",", values) + "]";
        }
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        return mapper.writeValueAsString(node);
    }
}
