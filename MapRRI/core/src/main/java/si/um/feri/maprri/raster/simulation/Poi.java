package si.um.feri.maprri.raster.simulation;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.List;

import si.um.feri.maprri.raster.utils.MapObject;

public class Poi {
    public MapObject poi;

    public float fill = 0f;
    public float urgency = 0f;
    public float fillRate = 0f;
    public float smell = 0f;
    public float temperature;
    public String status;

    public boolean isEcoIsland;

    public List<Poi> binsInRange = new ArrayList<>();
    public Array<SmellParticle> smellParticles = new Array<>();

    public Texture[] fillTextures;
    public Array<TrashParticle> particles;

    public Poi(MapObject poi) {
        this.poi = poi;
        this.isEcoIsland = poi.type.equals("eco-island");
    }
}
