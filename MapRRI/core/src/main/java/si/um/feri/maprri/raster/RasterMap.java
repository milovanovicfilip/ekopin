package si.um.feri.maprri.raster;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import java.io.IOException;
import java.util.List;

import si.um.feri.maprri.raster.api.MapDataService;
import si.um.feri.maprri.raster.utils.Constants;
import si.um.feri.maprri.raster.utils.Geolocation;
import si.um.feri.maprri.raster.utils.MapObject;
import si.um.feri.maprri.raster.utils.MapRasterTiles;
import si.um.feri.maprri.raster.utils.ZoomXY;

public class RasterMap extends ApplicationAdapter implements GestureDetector.GestureListener {

    private ShapeRenderer shapeRenderer;
    private Vector3 touchPosition;

    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;
    private OrthographicCamera camera;

    private Texture[] mapTiles;
    private ZoomXY beginTile;
    private Stage stage;
    private Skin skin;
    private InputMultiplexer multiplexer;

    private SpriteBatch batch;
    private Texture pinBin;
    private Texture pinDisposal;
    private Texture pinRecycle;
    private Texture myLocation;
    private Texture backgroundTexture;
    private Map<String, Texture> iconMap;

    private java.util.List<MapObject> allObjects;
    private java.util.List<MapObject> poiObjects;

    private Table infoPanel;
    private Label infoLabel;
    private MapObject selectedPOI;

    private float markerBaseSize = 84f;
    private float markerSizeCurrent = markerBaseSize;
    private float markerSizeTarget = markerBaseSize;
    private final float markerSizeMin = 36f;
    private final float markerSizeMax = 200f;
    private final float markerSmoothingSpeed = 12f;

    private float targetZoom;
    private final float minZoom = 0.3f;
    private final float maxZoom = 2f;
    private final float zoomStep = 0.34f;
    private final float pinchStep = 0.05f;
    private final float scrollZoomMultiplier = 0.15f;
    private final float zoomSmoothingSpeed = 6f;




    private final Geolocation CENTER_GEOLOCATION = new Geolocation(46.557314, 15.637771);

    private final Geolocation MARKER_GEOLOCATION = new Geolocation(46.559070, 15.638100);

    @Override
    public void create() {

        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Constants.MAP_WIDTH, Constants.MAP_HEIGHT);
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.viewportWidth = Constants.MAP_WIDTH / 2f;
        camera.viewportHeight = Constants.MAP_HEIGHT / 2f;
        camera.zoom = 2f;
        camera.update();

        targetZoom = camera.zoom;

        touchPosition = new Vector3();

        stage = new Stage(new ScreenViewport());
        skin = new Skin();
        BitmapFont font = new BitmapFont();
        skin.add("default-font", font);
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        skin.add("default", labelStyle);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0, 0, 0, 0.7f));
        pixmap.fill();
        backgroundTexture = new Texture(pixmap);
        pixmap.dispose();
        TextureRegionDrawable backgroundDrawable = new TextureRegionDrawable(new TextureRegion(backgroundTexture));

        Texture plusTexture = new Texture("plus.png");
        Texture minusTexture = new Texture("minus.png");

        ImageButton zoomInButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(plusTexture)));
        zoomInButton.setSize(40, 40);
        zoomInButton.setPosition(Gdx.graphics.getWidth() - zoomInButton.getWidth() - 10, Gdx.graphics.getHeight() - zoomInButton.getHeight() - 10);
        zoomInButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                targetZoom -= zoomStep;
                targetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
            }
        });

        ImageButton zoomOutButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(minusTexture)));
        zoomOutButton.setSize(40, 40);
        zoomOutButton.setPosition(Gdx.graphics.getWidth() - zoomOutButton.getWidth() - 10, Gdx.graphics.getHeight() - zoomInButton.getHeight() - zoomOutButton.getHeight() - 20);
        zoomOutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                targetZoom += zoomStep;
                targetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
            }
        });

        stage.addActor(zoomInButton);
        stage.addActor(zoomOutButton);

        infoPanel = new Table();
        infoPanel.setSize(200, 80);
        infoPanel.setPosition(10, 10);
        infoPanel.setBackground(backgroundDrawable);

        infoLabel = new Label("Kliknite na marker", skin);
        infoLabel.setWrap(true);
        infoPanel.add(infoLabel).pad(5).width(190);
        infoPanel.row();

        stage.addActor(infoPanel);

        GestureDetector gd = new GestureDetector(this);

        InputAdapter scrollProcessor = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                targetZoom += amountY * scrollZoomMultiplier;
                targetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
                return true;
            }
        };

        multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(scrollProcessor);
        multiplexer.addProcessor(gd);

        Gdx.input.setInputProcessor(multiplexer);


        MapDataService service = new MapDataService();
        allObjects = service.fetchObjects();

        System.out.println("Loaded objects: " + (allObjects == null ? 0 : allObjects.size()));

        if (allObjects != null) {
            for (MapObject o : allObjects) {
                System.out.println(o);
            }
        }

        try {
            ZoomXY centerTile = MapRasterTiles.getTileNumber(CENTER_GEOLOCATION.lat, CENTER_GEOLOCATION.lng, Constants.ZOOM);
            mapTiles = MapRasterTiles.getRasterTileZone(centerTile, Constants.NUM_TILES);
            beginTile = new ZoomXY(Constants.ZOOM, centerTile.x - ((Constants.NUM_TILES - 1) / 2), centerTile.y - ((Constants.NUM_TILES - 1) / 2));
        } catch (IOException e) {
            e.printStackTrace();
        }

        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(Constants.NUM_TILES, Constants.NUM_TILES, MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE);
        int index = 0;
        for (int j = Constants.NUM_TILES - 1; j >= 0; j--) {
            for (int i = 0; i < Constants.NUM_TILES; i++) {
                TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                cell.setTile(new StaticTiledMapTile(new TextureRegion(mapTiles[index], MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE)));
                layer.setCell(i, j, cell);
                index++;
            }
        }
        layers.add(layer);

        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        batch = new SpriteBatch();
        pinBin = new Texture("pin-bin.png");
        pinDisposal = new Texture("pin-disposal.png");
        pinRecycle = new Texture("pin-recycle.png");
        myLocation = new Texture("my-location.png");
        iconMap = new HashMap<>();
        iconMap.put("bin", pinBin);
        iconMap.put("disposal-site", pinDisposal);
        iconMap.put("disposal", pinDisposal);
        iconMap.put("eco-island", pinRecycle);
        iconMap.put("eco", pinRecycle);

        filterPOIsToMapBounds(allObjects);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0, 0, 0, 1);

        handleInput();

        float tz = MathUtils.clamp(Gdx.graphics.getDeltaTime() * zoomSmoothingSpeed, 0f, 1f);
        camera.zoom = MathUtils.lerp(camera.zoom, targetZoom, tz);
        camera.zoom = MathUtils.clamp(camera.zoom, minZoom, maxZoom);

        camera.update();

        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();

        drawMarkers();

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();

    }

    private void drawMarkers() {
        if (beginTile == null || poiObjects == null || poiObjects.isEmpty()) {
            return;
        }

        markerSizeTarget = MathUtils.clamp(markerBaseSize * camera.zoom, markerSizeMin, markerSizeMax);
        float t = MathUtils.clamp(Gdx.graphics.getDeltaTime() * markerSmoothingSpeed, 0f, 1f);
        markerSizeCurrent = MathUtils.lerp(markerSizeCurrent, markerSizeTarget, t);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (MapObject o : poiObjects) {
            Vector2 pos = MapRasterTiles.getPixelPosition(o.lat, o.lon, beginTile.x, beginTile.y);
            Texture tex = (o == selectedPOI) ? myLocation : iconMap.getOrDefault(o.type, pinBin);
            float size = markerSizeCurrent;
            batch.draw(tex, pos.x - size / 2f, pos.y - size / 2f, size, size);
        }
        batch.end();
    }

    private void filterPOIsToMapBounds(java.util.List<MapObject> all) {
        poiObjects = new ArrayList<>();
        if (all == null || beginTile == null) return;

        int z = Constants.ZOOM;
        double leftLon = MapRasterTiles.tile2long(beginTile.x, z);
        double rightLon = MapRasterTiles.tile2long(beginTile.x + Constants.NUM_TILES, z);
        double topLat = MapRasterTiles.tile2lat(beginTile.y, z);
        double bottomLat = MapRasterTiles.tile2lat(beginTile.y + Constants.NUM_TILES, z);

        for (MapObject o : all) {
            if (o.lon >= leftLon && o.lon <= rightLon && o.lat <= topLat && o.lat >= bottomLat) {
                poiObjects.add(o);
            }
        }

        System.out.println("POIs in current map bounds: " + poiObjects.size());
    }

    private void updateInfoPanel() {
        if (selectedPOI != null) {
            String typeDisplay = selectedPOI.type;
            infoLabel.setText("Tip: " + typeDisplay + "\nKoordinate: " + String.format("%.6f", selectedPOI.lat) + ", " + String.format("%.6f", selectedPOI.lon));
        } else {
            infoLabel.setText("Kliknite na marker");
        }
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        stage.dispose();
        skin.dispose();

        if (batch != null) batch.dispose();
        if (pinBin != null) pinBin.dispose();
        if (pinDisposal != null) pinDisposal.dispose();
        if (pinRecycle != null) pinRecycle.dispose();
        if (myLocation != null) myLocation.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();

    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        touchPosition.set(x, y, 0);
        camera.unproject(touchPosition);
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        Vector3 worldPos = new Vector3(x, y, 0);
        camera.unproject(worldPos);

        if (poiObjects != null) {
            for (MapObject poi : poiObjects) {
                Vector2 pos = MapRasterTiles.getPixelPosition(poi.lat, poi.lon, beginTile.x, beginTile.y);
                float size = markerSizeCurrent;
                if (worldPos.x >= pos.x - size / 2 && worldPos.x <= pos.x + size / 2 &&
                    worldPos.y >= pos.y - size / 2 && worldPos.y <= pos.y + size / 2) {
                    selectedPOI = poi;
                    updateInfoPanel();
                    return true;
                }
            }
        }

        selectedPOI = null;
        updateInfoPanel();
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }


    @Override
    public boolean fling(float velocityX, float velocityY, int button) {
        return false;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        camera.translate(-deltaX, deltaY);
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        if (initialDistance >= distance)
            targetZoom += pinchStep;
        else
            targetZoom -= pinchStep;
        targetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
        return true;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        return false;
    }

    @Override
    public void pinchStop() {

    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            targetZoom += zoomStep * 0.25f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            targetZoom -= zoomStep * 0.25f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.translate(-3, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.translate(3, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.translate(0, -3, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.translate(0, 3, 0);
        }

        targetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
        float effectiveViewportWidth = camera.viewportWidth * camera.zoom;
        float effectiveViewportHeight = camera.viewportHeight * camera.zoom;

        camera.position.x = MathUtils.clamp(camera.position.x, effectiveViewportWidth / 2f, Constants.MAP_WIDTH - effectiveViewportWidth / 2f);
        camera.position.y = MathUtils.clamp(camera.position.y, effectiveViewportHeight / 2f, Constants.MAP_HEIGHT - effectiveViewportHeight / 2f);
    }
}
