package si.um.feri.maprri.raster.simulation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

public class SmellParticle {
    public Texture texture;
    public Vector2 position;
    public Vector2 velocity;

    public float life;
    public float age = 0f;
    public float alpha = 1f;

    public SmellParticle(Texture texture, Vector2 startPos, Vector2 velocity, float lifeSeconds) {
        this.texture = texture;
        this.position = new Vector2(startPos);
        this.velocity = new Vector2(velocity);
        this.life = lifeSeconds;
    }

    public boolean update(float dt) {
        age += dt;
        position.mulAdd(velocity, dt);

        float t = age / life;
        alpha = 1f - Math.min(1f, t);

        return age >= life;
    }
}
