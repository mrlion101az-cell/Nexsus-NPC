package com.nexusuniverse.npc.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches a real player's current skin from Mojang's public APIs, the same two-step lookup
 * Citizens' own {@code /npc skin <name>} uses under the hood: username -> UUID (api.mojang.com),
 * then UUID -> profile properties, which is where the actual base64 "textures" value + signature
 * live (sessionserver.mojang.com). Both calls run off the main thread; only the resulting
 * {@link SkinData} is ever handed back to Bukkit-thread code.
 * <p>
 * NOTE: com.google.gson is used here for JSON parsing rather than adding a new dependency --
 * Paper's own API already depends on Gson and ships it on the server's classpath, so this doesn't
 * require anything beyond what paper-api already pulls in.
 */
public class SkinFetcher {

    private static final String UUID_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_LOOKUP = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final JavaPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, SkinData> cache = new ConcurrentHashMap<>();
    private final long cacheMillis;

    public SkinFetcher(JavaPlugin plugin, int cacheMinutes) {
        this.plugin = plugin;
        this.cacheMillis = cacheMinutes * 60_000L;
    }

    /** Never throws synchronously -- failures complete the future exceptionally with a message safe to show a player. */
    public CompletableFuture<SkinData> fetch(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        SkinData cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis() < cacheMillis) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> lookupUuid(username))
                .thenCompose(this::lookupProfile)
                .thenApply(skin -> {
                    cache.put(key, skin);
                    return skin;
                });
    }

    private String lookupUuid(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(UUID_LOOKUP + username))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new SkinLookupException("No such player \"" + username + "\" (checked Mojang's records).");
            }
            if (response.statusCode() != 200) {
                throw new SkinLookupException("Mojang's username lookup returned HTTP " + response.statusCode() + " -- try again shortly.");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.get("id").getAsString();
        } catch (SkinLookupException e) {
            throw e;
        } catch (Exception e) {
            throw new SkinLookupException("Couldn't reach Mojang's username lookup: " + e.getMessage());
        }
    }

    private CompletableFuture<SkinData> lookupProfile(String uuidNoDashes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_LOOKUP + uuidNoDashes + "?unsigned=false"))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new SkinLookupException("Mojang's profile lookup returned HTTP " + response.statusCode() + " -- try again shortly.");
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray properties = json.getAsJsonArray("properties");
                for (var element : properties) {
                    JsonObject property = element.getAsJsonObject();
                    if (property.get("name").getAsString().equals("textures")) {
                        String value = property.get("value").getAsString();
                        String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                        if (signature == null) {
                            throw new SkinLookupException("That player's profile has no signed texture data to copy.");
                        }
                        return new SkinData(value, signature, System.currentTimeMillis());
                    }
                }
                throw new SkinLookupException("That player's profile has no skin set.");
            } catch (SkinLookupException e) {
                throw e;
            } catch (Exception e) {
                throw new SkinLookupException("Couldn't reach Mojang's profile lookup: " + e.getMessage());
            }
        });
    }

    /** Thrown (wrapped in a CompletionException by the CompletableFuture chain) with a message that's safe to show directly to a player. */
    public static class SkinLookupException extends RuntimeException {
        public SkinLookupException(String message) {
            super(message);
        }
    }
}
