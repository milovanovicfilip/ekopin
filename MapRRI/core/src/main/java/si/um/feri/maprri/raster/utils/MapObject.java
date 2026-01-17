package si.um.feri.maprri.raster.utils;

public class MapObject {

    public final String id;
    public double lat;
    public double lon;
    public final String type;

    public MapObject(String id, double lat, double lon, String type) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.type = type;
    }

    @Override
    public String toString() {
        return "MapObject{" +
            "id=" + id +
            ", lat=" + lat +
            ", lon=" + lon +
            ", type=" + type +
            '}';
    }

}
