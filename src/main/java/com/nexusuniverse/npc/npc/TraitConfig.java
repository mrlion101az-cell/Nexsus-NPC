package com.nexusuniverse.npc.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A persisted, generic "this NPC has trait X with these settings" record. Kept generic (a type
 * name + a param map) rather than one Java class per trait so npcs.yml doesn't need a bespoke
 * serializer for every trait that gets added later -- {@link com.nexusuniverse.npc.trait.Trait}
 * implementations read whatever keys they care about out of {@link #params()}.
 */
public class TraitConfig {

    private final String type;
    private final Map<String, Object> params;

    public TraitConfig(String type, Map<String, Object> params) {
        this.type = type;
        this.params = params == null ? new LinkedHashMap<>() : params;
    }

    public String type() {
        return type;
    }

    public Map<String, Object> params() {
        return params;
    }

    @SuppressWarnings("unchecked")
    public List<String> stringList(String key) {
        Object raw = params.get(key);
        return raw instanceof List ? (List<String>) raw : List.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.putAll(params);
        return map;
    }

    public static TraitConfig fromMap(Map<?, ?> raw) {
        Map<String, Object> params = new LinkedHashMap<>();
        String type = null;
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.equals("type")) {
                type = String.valueOf(entry.getValue());
            } else {
                params.put(key, entry.getValue());
            }
        }
        return new TraitConfig(type, params);
    }
}
