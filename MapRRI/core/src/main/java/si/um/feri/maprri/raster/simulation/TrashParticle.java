package si.um.feri.maprri.raster.simulation;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

public class TrashParticle {
    public Texture texture;
    public Vector2 position;
    Vector2 target;
    float speed;

    public TrashParticle(Texture tex, Vector2 start, Vector2 end, float spd) {
        texture = tex;
        position = new Vector2(start);
        target = new Vector2(end);
        speed = spd;
    }

    public boolean update(float delta) {
        Vector2 dir = target.cpy().sub(position).nor().scl(speed * delta);
        position.add(dir);
        return position.dst(target) < 2f;
    }
}
