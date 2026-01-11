package si.um.feri.maprri.raster.api.mapper;

import si.um.feri.maprri.raster.api.dto.MapObjectDTO;
import si.um.feri.maprri.raster.utils.MapObject;

public class MapObjectMapper {

    public static MapObject toModel(MapObjectDTO dto) {
        return new MapObject(
            dto.lat,
            dto.lon,
            dto.type
        );
    }
}
