package dev.besan.browserbrake;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlaceStore {
    private static final String KEY_PLACES = "places_json";
    private static final String KEY_SELECTED = "selected_place_ids";
    private static final String KEY_ALL = "all_places";

    private PlaceStore() {}

    public static List<Place> all(Context c) {
        List<Place> out = new ArrayList<>();
        String raw = Prefs.p(c).getString(KEY_PLACES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Place.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static Place add(Context c, String name, double lat, double lon, float radiusM) {
        Place p = new Place(UUID.randomUUID().toString(), name, lat, lon, radiusM);
        List<Place> items = all(c);
        items.add(p);
        saveAll(c, items);
        return p;
    }

    public static void delete(Context c, String id) {
        List<Place> items = all(c);
        items.removeIf(p -> p.id.equals(id));
        saveAll(c, items);
        Set<String> selected = selectedIds(c);
        selected.remove(id);
        setSelectedIds(c, selected);
    }

    private static void saveAll(Context c, List<Place> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Place p : items) arr.put(p.toJson());
        } catch (Exception ignored) {}
        Prefs.p(c).edit().putString(KEY_PLACES, arr.toString()).apply();
    }

    public static boolean isAllPlaces(Context c) {
        return Prefs.p(c).getBoolean(KEY_ALL, true);
    }

    public static void setAllPlaces(Context c, boolean all) {
        Prefs.p(c).edit().putBoolean(KEY_ALL, all).apply();
    }

    public static Set<String> selectedIds(Context c) {
        return new HashSet<>(Prefs.p(c).getStringSet(KEY_SELECTED, new HashSet<>()));
    }

    public static void setSelectedIds(Context c, Set<String> ids) {
        Prefs.p(c).edit().putStringSet(KEY_SELECTED, new HashSet<>(ids)).apply();
    }

    public static boolean matches(Context c, Location location) {
        if (isAllPlaces(c)) return true;
        if (location == null) return Prefs.p(c).getBoolean("last_context_place_match", false);

        Set<String> selected = selectedIds(c);
        if (selected.isEmpty()) return false;

        float best = Float.MAX_VALUE;
        boolean hit = false;
        for (Place p : all(c)) {
            if (!selected.contains(p.id)) continue;
            float[] result = new float[1];
            Location.distanceBetween(p.lat, p.lon, location.getLatitude(), location.getLongitude(), result);
            best = Math.min(best, result[0]);
            if (result[0] <= p.radiusM) hit = true;
        }

        Prefs.p(c).edit()
                .putBoolean("last_context_place_match", hit)
                .putFloat("last_place_distance_m", best == Float.MAX_VALUE ? -1f : best)
                .putLong("last_location_time", System.currentTimeMillis())
                .apply();
        return hit;
    }

    public static float lastDistance(Context c) {
        return Prefs.p(c).getFloat("last_place_distance_m", -1f);
    }
}
