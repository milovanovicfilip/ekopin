package si.um.feri.maprri.raster.utils;

public class MapObject {

    public final double lat;
    public final double lon;
    public final String type;

    public MapObject(double lat, double lon, String type) {
        this.lat = lat;
        this.lon = lon;
        this.type = type;
    }

    @Override
    public String toString() {
        return "MapObject{" +
            "lat=" + lat +
            ", lon=" + lon +
            ", type=" + type +
            '}';
    }

}
