package dev.besan.browserbrake;

import org.json.JSONException;
import org.json.JSONObject;

public final class Place {
    public final String id;
    public final String name;
    public final double lat;
    public final double lon;
    public final float radiusM;

    public Place(String id, String name, double lat, double lon, float radiusM) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.radiusM = radiusM;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("radiusM", radiusM);
        return o;
    }

    public static Place fromJson(JSONObject o) throws JSONException {
        return new Place(
                o.getString("id"),
                o.getString("name"),
                o.getDouble("lat"),
                o.getDouble("lon"),
                (float) o.optDouble("radiusM", 200.0)
        );
    }
}
