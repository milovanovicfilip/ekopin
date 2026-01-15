package si.um.feri.maprri.raster.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import si.um.feri.maprri.raster.api.dto.MapObjectDTO;
import si.um.feri.maprri.raster.api.mapper.MapObjectMapper;
import si.um.feri.maprri.raster.utils.MapObject;

public class MapDataService {

    private static final String API_URL = "http://localhost:5000/api/poi";

    public List<MapObject> fetchObjects() {
        List<MapObject> objects = new ArrayList<>();
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error: " + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONArray array = new JSONArray(sb.toString());

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject obj = array.getJSONObject(i);

                    JSONArray coords = null;
                    if (obj.has("location") && obj.getJSONObject("location").has("coordinates")) {
                        coords = obj.getJSONObject("location").getJSONArray("coordinates");
                    } else if (obj.has("geometry") && obj.getJSONObject("geometry").has("coordinates")) {
                        coords = obj.getJSONObject("geometry").getJSONArray("coordinates");
                    } else if (obj.has("coordinates")) {
                        coords = obj.getJSONArray("coordinates");
                    }

                    if (coords == null || coords.length() < 2) {
                        continue;
                    }

                    double lon = coords.getDouble(0);
                    double lat = coords.getDouble(1);

                    String poiType = obj.optString("type", "");
                    if ("Point".equalsIgnoreCase(poiType) || poiType.isEmpty()) {
                        poiType = obj.optString("poiType", obj.optString("category", "unknown"));
                    }

                    objects.add(new MapObject(lat, lon, poiType));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return objects;
    }
}
