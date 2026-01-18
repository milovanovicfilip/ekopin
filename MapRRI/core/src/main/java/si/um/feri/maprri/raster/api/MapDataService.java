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

    private static final String API_URL = "http://localhost:3000/api/poi";

    public void updatePoiLocation(String poiId, double lat, double lon) {
        if (poiId == null || poiId.isEmpty()) return;

        new Thread(() -> {
            try {
                URL url = new URL(API_URL + "/" + poiId + "/location");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("lat", lat);
                body.put("lon", lon);

                byte[] outputBytes = body.toString().getBytes("UTF-8");
                conn.getOutputStream().write(outputBytes);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                    System.err.println("Failed to update POI location: " + responseCode);
                } else {
                    System.out.println("POI updated successfully: " + poiId);
                }

                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


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

                    String id = obj.optString("_id", null);
                    double lon = coords.getDouble(0);
                    double lat = coords.getDouble(1);

                    String poiType = obj.optString("type", "");
                    if ("Point".equalsIgnoreCase(poiType) || poiType.isEmpty()) {
                        poiType = obj.optString("poiType", obj.optString("category", "unknown"));
                    }

                    objects.add(new MapObject(id, lat, lon, poiType));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return objects;
    }

    public List<BinStatus> fetchBinStatuses() {
        List<BinStatus> out = new ArrayList<>();
        try {
            URL url = new URL("http://localhost:3000/api/binstatus/");
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
                JSONObject obj = array.getJSONObject(i);

                String poiId = null;
                Object poiField = obj.opt("poiId");
                if (poiField instanceof JSONObject) {
                    poiId = ((JSONObject) poiField).optString("_id", null);
                } else if (poiField instanceof String) {
                    poiId = (String) poiField;
                }

                String status = obj.optString("status", null);

                if (poiId == null) continue;
                if (!"full".equals(status) && !"empty".equals(status)) continue;

                out.add(new BinStatus(poiId, status));
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }


    public static class BinStatus {
        public final String poiId;
        public final String status;

        public BinStatus(String poiId, String status) {
            this.poiId = poiId;
            this.status = status;
        }
    }
}
