package si.um.feri.maprri.raster;

import static si.um.feri.maprri.raster.utils.Constants.ZOOM;
import static si.um.feri.maprri.raster.utils.MapRasterTiles.TILE_SIZE;
import static si.um.feri.maprri.raster.utils.MapRasterTiles.tile2lat;
import static si.um.feri.maprri.raster.utils.MapRasterTiles.tile2long;

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
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import java.io.IOException;
import java.util.List;

import si.um.feri.maprri.raster.api.MapDataService;
import si.um.feri.maprri.raster.simulation.Poi;
import si.um.feri.maprri.raster.simulation.TrashParticle;
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
    private Texture weatherBackgroundTexture;
    private Map<String, Texture> iconMap;

    private java.util.List<MapObject> allObjects;
    private java.util.List<MapObject> poiObjects;

    private Table infoPanel;
    private Label infoLabel;
    private MapObject selectedPOI;

    private Table weatherPanel;
    private Image weatherIcon;
    private Label weatherTemp;
    private Texture weatherTexture;

    private Texture buttonBin;
    private Texture buttonEcoIsland;
    private Texture buttonDisposalSite;
    private Texture buttonHidden;
    private ImageButton binButton;
    private ImageButton ecoIslandButton;
    private ImageButton disposalSiteButton;

    private boolean binVisible = true;
    private boolean ecoIslandVisible = true;
    private boolean disposalSiteVisible = true;

    private float markerBaseSize = 84f;
    private float markerSizeCurrent = markerBaseSize;
    private float markerSizeTarget = markerBaseSize;
    private final float markerSizeMin = 36f;
    private final float markerSizeMax = 200f;
    private final float markerSmoothingSpeed = 12f;

    private float targetZoom;
    private final float minZoom = 0.3f;
    private final float maxZoom = 2.f;
    private final float zoomStep = 0.34f;
    private final float pinchStep = 0.05f;
    private final float scrollZoomMultiplier = 0.15f;
    private final float zoomSmoothingSpeed = 6f;

    private boolean simulationMode = false;

    private List<Poi> simulatedBins = new ArrayList<>();

    private Texture progressTex;

    float currentTemperature = 23f;


    private float simulationSpeed = 0.25f;


    private final Geolocation CENTER_GEOLOCATION = new Geolocation(46.557314, 15.637771);

    private final Geolocation MARKER_GEOLOCATION = new Geolocation(46.559070, 15.638100);

    float BASE_BIN_RATE = 0.02f;
    float ECO_MULTIPLIER = 0.5f;
    float RANGE_PENALTY = 0.15f;

    private static final float BAR_WIDTH = 30f;
    private static final float BAR_HEIGHT = 4f;
    private static final float BAR_OFFSET_Y = -10f;
    private static final float URGENCY_BAR_HEIGHT = 4f;
    private static final float URGENCY_BAR_OFFSET_Y = -16f;

    float BIN_URGENCY_RATE = 0.08f;
    float ECO_URGENCY_RATE = 0.03f;

    float TEMP_URGENCY_MULT = 0.015f;
    float FILL_URGENCY_MULT = 0.6f;

    float SMELL_THRESHOLD = 0.6f;
    float MAX_SMELL = 1f;

    private float barWidthCurrent = 35f;
    private float barHeightCurrent = 5f;
    private final float barWidthBase = 35f;
    private final float barHeightBase = 5f;
    private final float barSmoothingSpeed = 6f;



    @Override
    public void create() {

        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Constants.MAP_WIDTH, Constants.MAP_HEIGHT);
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.viewportWidth = Constants.MAP_WIDTH / 2f;
        camera.viewportHeight = Constants.MAP_HEIGHT / 2f;
        camera.zoom = 1f;
        camera.update();

        targetZoom = camera.zoom;

        touchPosition = new Vector3();

        stage = new Stage(new FitViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        BitmapFont font = new BitmapFont();
        skin.add("font-export", font);
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        skin.add("default", labelStyle);

        Label.LabelStyle weatherLabelStyle = new Label.LabelStyle(labelStyle);
        skin.add("weather", weatherLabelStyle);

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

        buttonBin = new Texture("button-bin.png");
        buttonEcoIsland = new Texture("button-ecoisland.png");
        buttonDisposalSite = new Texture("button-disposalsite.png");
        buttonHidden = new Texture("button-hidden.png");

        binButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(buttonBin)));
        binButton.setSize(50, 50);
        binButton.setPosition(Gdx.graphics.getWidth() - 60, 10);
        binButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                binVisible = !binVisible;
                TextureRegionDrawable current = (TextureRegionDrawable) binButton.getStyle().imageUp;
                if (current.getRegion().getTexture() == buttonBin) {
                    binButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonHidden));
                } else {
                    binButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonBin));
                }
            }
        });
        stage.addActor(binButton);

        ecoIslandButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(buttonEcoIsland)));
        ecoIslandButton.setSize(50, 50);
        ecoIslandButton.setPosition(Gdx.graphics.getWidth() - 120, 10);
        ecoIslandButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ecoIslandVisible = !ecoIslandVisible;
                TextureRegionDrawable current = (TextureRegionDrawable) ecoIslandButton.getStyle().imageUp;
                if (current.getRegion().getTexture() == buttonEcoIsland) {
                    ecoIslandButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonHidden));
                } else {
                    ecoIslandButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonEcoIsland));
                }
            }
        });
        stage.addActor(ecoIslandButton);

        disposalSiteButton = new ImageButton(new TextureRegionDrawable(new TextureRegion(buttonDisposalSite)));
        disposalSiteButton.setSize(50, 50);
        disposalSiteButton.setPosition(Gdx.graphics.getWidth() - 180, 10);
        disposalSiteButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                disposalSiteVisible = !disposalSiteVisible;
                TextureRegionDrawable current = (TextureRegionDrawable) disposalSiteButton.getStyle().imageUp;
                if (current.getRegion().getTexture() == buttonDisposalSite) {
                    disposalSiteButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonHidden));
                } else {
                    disposalSiteButton.getStyle().imageUp = new TextureRegionDrawable(new TextureRegion(buttonDisposalSite));
                }
            }
        });
        stage.addActor(disposalSiteButton);

        infoPanel = new Table();
        infoPanel.setSize(200, 80);
        infoPanel.setPosition(10, 10);
        infoPanel.setBackground(backgroundDrawable);

        infoLabel = new Label("Kliknite na marker", skin);
        infoLabel.setWrap(true);
        infoPanel.add(infoLabel).pad(5).width(190);
        infoPanel.row();

        stage.addActor(infoPanel);

        weatherTexture = new Texture("day_partial_cloud.png");
        weatherIcon = new Image(weatherTexture);
        weatherPanel = new Table();

        Pixmap weatherPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        weatherPixmap.setColor(new Color(0, 0, 0, 0.7f));
        weatherPixmap.fill();
        weatherBackgroundTexture = new Texture(weatherPixmap);
        weatherPixmap.dispose();
        TextureRegionDrawable weatherBackground = new TextureRegionDrawable(new TextureRegion(weatherBackgroundTexture));
        weatherPanel.setBackground(weatherBackground);

        weatherPanel.setSize(150, 150);
        weatherPanel.setPosition(10, Gdx.graphics.getHeight() - weatherPanel.getHeight() - 10);
        weatherPanel.center().center();

        weatherPanel.add(weatherIcon).size(50, 50).padTop(10).row();

        final TextField tempField = new TextField("23", skin, "spinner");
        tempField.setAlignment(1);
        tempField.setTextFieldListener((textField, c) -> {
            if (!Character.isDigit(c)) return;
        });

        Table spinnerTable = new Table();
        spinnerTable.left();

        Button minusButton = new Button(skin, "spinner-minus");
        minusButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int current = Integer.parseInt(tempField.getText());
                current--;
                tempField.setText(String.valueOf(current));
                currentTemperature = current;

            }
        });
        spinnerTable.add(minusButton).size(30, 30).padRight(5);

        spinnerTable.add(tempField).size(50, 30);

        Button plusButton = new Button(skin, "spinner-plus");
        plusButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int current = Integer.parseInt(tempField.getText());
                current++;
                tempField.setText(String.valueOf(current));
                currentTemperature = current;
            }
        });
        spinnerTable.add(plusButton).size(30, 30).padLeft(5);

        weatherPanel.add(spinnerTable).padTop(5).row();

        Label speedLabel = new Label("Hitrost simulacije", skin);

        Slider speedSlider = new Slider(0.05f, 2.0f, 0.05f, false, skin);
        speedSlider.setValue(simulationSpeed);

        Label speedValue = new Label("0.25x", skin);

        speedSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                simulationSpeed = speedSlider.getValue();
                speedValue.setText(String.format("%.2fx", simulationSpeed));
            }
        });

        weatherPanel.add(speedLabel).padTop(8).row();
        weatherPanel.add(speedSlider).width(120).padTop(4).row();
        weatherPanel.add(speedValue).padTop(2).row();


        stage.addActor(weatherPanel);



//        Table sidebar = new Table();
//        sidebar.setSize(250, Gdx.graphics.getHeight());
//        sidebar.setPosition(Gdx.graphics.getWidth(), 0);
//        sidebar.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.7f)));
//        stage.addActor(sidebar);
//
//        Label sidebarTitle = new Label("Simulacija", skin);
//        sidebar.add(sidebarTitle).pad(10).row();
//
//        Label urgencyLabel = new Label("Urgenca: Niska", skin);
//        sidebar.add(urgencyLabel).pad(5).row();
//
//        Label tempLabel = new Label("Temperatura: 23°C", skin);
//        sidebar.add(tempLabel).pad(5).row();
//
//        Texture trashTexture = new Texture("garbage-assets/bins/blue-bin-01.png");
//        Image trashImage = new Image(trashTexture);
//        trashImage.setSize(200, 150);
//        sidebar.add(trashImage).padTop(20).row();


        CheckBox simSwitch = new CheckBox("Vkopi simulacijo", skin, "switch-text");
        simSwitch.setPosition(weatherPanel.getWidth() + 20, Gdx.graphics.getHeight() - simSwitch.getHeight() - 10);
        simSwitch.getLabelCell().padLeft(15);
        simSwitch.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                simulationMode = simSwitch.isChecked();
                System.out.println("Simulation: " + simulationMode);

                if (!simulationMode){
                    for(Poi p : simulatedBins){
                        p.particles.clear();
                        p.fill=0;
                        p.fillRate=0;
                        p.urgency=0;
                    }
                }

            }
        });
        stage.addActor(simSwitch);



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
            ZoomXY centerTile = MapRasterTiles.getTileNumber(CENTER_GEOLOCATION.lat, CENTER_GEOLOCATION.lng, ZOOM);
            mapTiles = MapRasterTiles.getRasterTileZone(centerTile, Constants.NUM_TILES);
            beginTile = new ZoomXY(ZOOM, centerTile.x - ((Constants.NUM_TILES - 1) / 2), centerTile.y - ((Constants.NUM_TILES - 1) / 2));
        } catch (IOException e) {
            e.printStackTrace();
        }

        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(Constants.NUM_TILES, Constants.NUM_TILES, TILE_SIZE, TILE_SIZE);
        int index = 0;
        for (int j = Constants.NUM_TILES - 1; j >= 0; j--) {
            for (int i = 0; i < Constants.NUM_TILES; i++) {
                TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                cell.setTile(new StaticTiledMapTile(new TextureRegion(mapTiles[index], TILE_SIZE, TILE_SIZE)));
                layer.setCell(i, j, cell);
                index++;
            }
        }
        layers.add(layer);

        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        batch = new SpriteBatch();
        pinBin = new Texture("garbage-assets/bins/blue-bin-01.png");
        pinDisposal = new Texture("garbage-assets/bins/red-bin-01.png");
        pinRecycle = new Texture("garbage-assets/bins/green-bin-01.png");
        myLocation = new Texture("my-location.png");
        iconMap = new HashMap<>();
        iconMap.put("bin", pinBin);
        iconMap.put("disposal-site", pinDisposal);
        iconMap.put("disposal", pinDisposal);
        iconMap.put("eco-island", pinRecycle);
        iconMap.put("eco", pinRecycle);

        filterPOIsToMapBounds(allObjects);

        initSimulationBins();

        for(Poi bin : simulatedBins){
            if(bin.poi.type.equals("bin")){
                bin.fillTextures = new Texture[]{
                    new Texture("garbage-assets/bins/blue-bin-01.png"),
                    new Texture("garbage-assets/bins/blue-bin-02.png"),
                    new Texture("garbage-assets/bins/blue-bin-03.png"),
                    new Texture("garbage-assets/bins/blue-bin-04.png")
                };
            }
            else if(bin.poi.type.equals("eco-island")){
                bin.fillTextures = new Texture[]{
                    new Texture("garbage-assets/bins/green-bin-01.png"),
                    new Texture("garbage-assets/bins/green-bin-02.png"),
                    new Texture("garbage-assets/bins/green-bin-03.png"),
                    new Texture("garbage-assets/bins/green-bin-04.png")
                };
            }
            else if(bin.poi.type.equals("disposal-site")){
                bin.fillTextures = new Texture[]{
                    new Texture("garbage-assets/bins/red-bin-01.png"),
                    new Texture("garbage-assets/bins/red-bin-02.png"),
                    new Texture("garbage-assets/bins/red-bin-03.png"),
                    new Texture("garbage-assets/bins/red-bin-04.png")
                };
            }

            bin.particles = new Array<>();
        }

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        Texture whiteTex = new Texture(pm);
        pm.dispose();

        progressTex = whiteTex;

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

        if (simulationMode) {
            updateSimulation(Gdx.graphics.getDeltaTime() * simulationSpeed);
        }

        drawMarkers();

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();

    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }


    private Color getUrgencyColor(float t) {
        t = MathUtils.clamp(t, 0f, 1f);

        if (t < 0.25f) {
            return new Color(0f + t * 4f, 1f, 0f, 1f);
        } else if (t < 0.5f) {
            float k = (t - 0.25f) * 4f;
            return new Color(1f, 1f - k * 0.5f, 0f, 1f);
        } else if (t < 0.75f) {
            float k = (t - 0.5f) * 4f;
            return new Color(1f, 0.5f - k * 0.5f, 0f, 1f);
        } else {
            return new Color(1f, 0f, 0f, 1f);
        }
    }

    private void drawProgressBar(SpriteBatch batch, Poi bin, float x, float y, float barWidthCurrent, float barHeightCurrent) {
        if (!simulationMode) return;

        float filled = bin.fill;
        float urgency = bin.urgency;

        float barX = x - barWidthCurrent / 2f;
        float barY = y - markerSizeCurrent / 2f - barHeightCurrent - 4f;

        batch.setColor(0, 0, 0, 0.6f);
        batch.draw(progressTex, barX, barY, barWidthCurrent, barHeightCurrent);

        if (filled > 0f) {
            batch.setColor(0.6f, 0.3f, 0.8f, 1f);
            batch.draw(progressTex, barX, barY, barWidthCurrent * filled, barHeightCurrent);
        }

        float urgencyY = barY - barHeightCurrent - 2f;

        batch.setColor(0, 0, 0, 0.4f);
        batch.draw(progressTex, barX, urgencyY, barWidthCurrent, barHeightCurrent);

        if (urgency > 0f) {
            batch.setColor(getUrgencyColor(urgency));
            batch.draw(progressTex, barX, urgencyY, barWidthCurrent * urgency, barHeightCurrent);
        }

        float minTemp = 0f;
        float maxTemp = 40f;

        float tempNorm = MathUtils.clamp(
            (bin.temperature - minTemp) / (maxTemp - minTemp),
            0f, 1f
        );

        float tempBarHeight = markerSizeCurrent * 0.9f;
        float tempBarWidth = barHeightCurrent;

        float tempBarX = x - markerSizeCurrent / 2f - tempBarWidth - 4f;
        float tempBarY = y - tempBarHeight / 2f;

        batch.setColor(0, 0, 0, 0.5f);
        batch.draw(progressTex, tempBarX, tempBarY, tempBarWidth, tempBarHeight);

        float filledHeight = tempBarHeight * tempNorm;

        batch.setColor(
            MathUtils.lerp(0f, 1f, tempNorm),   // R
            MathUtils.lerp(1f, 0f, tempNorm),   // G
            0f,
            1f
        );

        batch.draw(progressTex, tempBarX, tempBarY, tempBarWidth, filledHeight);

        batch.setColor(Color.WHITE);
    }



    private void drawMarkers() {
        if (beginTile == null || poiObjects == null || poiObjects.isEmpty()) {
            return;
        }

        markerSizeTarget = MathUtils.clamp(markerBaseSize * camera.zoom, markerSizeMin, markerSizeMax);
        float tMarker = MathUtils.clamp(Gdx.graphics.getDeltaTime() * markerSmoothingSpeed, 0f, 1f);
        markerSizeCurrent = MathUtils.lerp(markerSizeCurrent, markerSizeTarget, tMarker);

        float tBar = MathUtils.clamp(Gdx.graphics.getDeltaTime() * barSmoothingSpeed, 0f, 1f);
        barWidthCurrent = MathUtils.lerp(barWidthCurrent, barWidthBase, tBar);
        barHeightCurrent = MathUtils.lerp(barHeightCurrent, barHeightBase, tBar);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (Poi bin : simulatedBins) {
            MapObject o = bin.poi;

            if ((o.type.equals("bin") && binVisible) ||
                (o.type.equals("eco-island") && ecoIslandVisible) ||
                (o.type.equals("disposal-site") && disposalSiteVisible)) {
                Vector2 pos = MapRasterTiles.getPixelPosition(o.lat, o.lon, beginTile.x, beginTile.y);
                Texture tex = (o == selectedPOI) ? myLocation : iconMap.getOrDefault(o.type, pinBin);
                float size = markerSizeCurrent;
                float screenX = pos.x - size / 2f;
                float screenY = pos.y - size / 2f;

                batch.draw(tex, screenX, screenY, size, size);

                if(camera.zoom<1f){
                    drawProgressBar(batch, bin, pos.x,screenY,barWidthCurrent, barHeightCurrent);
                }
            }

            Vector2 pos = MapRasterTiles.getPixelPositionPrecise(o.lat, o.lon, beginTile.x, beginTile.y);

            int frameIndex = MathUtils.clamp((int)(bin.fill * bin.fillTextures.length), 0, bin.fillTextures.length-1);
            Texture tex = bin.fillTextures[frameIndex];

            batch.draw(tex, pos.x - markerSizeCurrent/2f, pos.y - markerSizeCurrent/2f, markerSizeCurrent, markerSizeCurrent);

            if(camera.zoom<1f){
                for(TrashParticle p : bin.particles){
                    batch.draw(p.texture, p.position.x, p.position.y, markerSizeCurrent*0.8f, markerSizeCurrent*0.8f);
                }
            }

        }



        batch.end();
    }



    private void filterPOIsToMapBounds(java.util.List<MapObject> all) {
        poiObjects = new ArrayList<>();
        if (all == null || beginTile == null) return;

        int z = ZOOM;
        double leftLon = tile2long(beginTile.x, z);
        double rightLon = tile2long(beginTile.x + Constants.NUM_TILES, z);
        double topLat = tile2lat(beginTile.y, z);
        double bottomLat = tile2lat(beginTile.y + Constants.NUM_TILES, z);

        for (MapObject o : all) {
            if (o.lon >= leftLon && o.lon <= rightLon && o.lat <= topLat && o.lat >= bottomLat) {
                poiObjects.add(o);
            }
        }

        System.out.println("POIs in current map bounds: " + poiObjects.size());
    }

    private void initSimulationBins() {
        simulatedBins.clear();

        if (poiObjects == null) return;

        for (MapObject o : poiObjects) {
            if (o.type.equals("bin") || o.type.equals("eco-island") || o.type.equals("disposal-site")) {
                simulatedBins.add(new Poi(o));
            }
        }
    }
    private void updateBinsInRange() {
        float RANGE = 150f;

        for (Poi bin : simulatedBins) {
            bin.binsInRange.clear();

            Vector2 binPos = MapRasterTiles.getPixelPosition(bin.poi.lat, bin.poi.lon, beginTile.x, beginTile.y);

            for (Poi other : simulatedBins) {
                if (bin == other) continue;

                Vector2 otherPos = MapRasterTiles.getPixelPosition(other.poi.lat, other.poi.lon, beginTile.x, beginTile.y);

                if (binPos.dst(otherPos) <= RANGE) {
                    bin.binsInRange.add(other);
                }
            }
        }
    }

    private void updateFillRates() {
        for (Poi bin : simulatedBins) {
            int nearby = bin.binsInRange.size();

            float rate = BASE_BIN_RATE / (1f + nearby * RANGE_PENALTY);

            if (bin.isEcoIsland) {
                rate *= ECO_MULTIPLIER;
            }

            bin.fillRate = rate;
        }
    }



    private void updateSimulation(float delta) {
        if (!simulationMode) return;

        updateBinsInRange();
        updateFillRates();

        for (Poi bin : simulatedBins) {
            if(!bin.poi.type.equals("disposal-site")){
                bin.temperature = currentTemperature * 0.8f;

                if (bin.poi.type.equals("eco-island")) {
                    bin.temperature *= 0.8f;
                }
            }

            if (bin.fill < 1f) {
                bin.fill += bin.fillRate * delta;
                bin.fill = MathUtils.clamp(bin.fill, 0f, 1f);
            }

            float baseUrgencyRate = bin.poi.type.equals("eco-island") ? 0.01f : 0.015f;

            float tempFactor = 1f + Math.max(0f, (bin.temperature - 20f) * 0.02f);

            float fillFactor = 1f + bin.fill * 0.2f;

            float urgencyIncrease = baseUrgencyRate * tempFactor * fillFactor * delta;

            bin.urgency += urgencyIncrease;
            bin.urgency = MathUtils.clamp(bin.urgency, 0f, 1f);

            if (bin.urgency < 0.6f) {
                bin.smell = MathUtils.lerp(bin.smell, 0f, delta * 1.5f);
            } else {
                float smellTarget = (bin.urgency - 0.6f) / 0.4f;
                if (bin.poi.type.equals("eco-island")) smellTarget *= 0.5f;
                bin.smell = MathUtils.lerp(bin.smell, MathUtils.clamp(smellTarget, 0f, 1f), delta * 2f);
            }

            if(bin.fill < 1f && MathUtils.random() < 0.02f){
                float offsetX = MathUtils.randomBoolean() ? -markerSizeCurrent*0.6f : markerSizeCurrent*0.6f;
                float offsetY = MathUtils.random(markerSizeCurrent*0.5f, markerSizeCurrent*1.2f);

                Vector2 binPos = MapRasterTiles.getPixelPosition(bin.poi.lat, bin.poi.lon, beginTile.x, beginTile.y);

                Vector2 start = new Vector2(binPos.x + offsetX, binPos.y + offsetY);
                Vector2 target = new Vector2(binPos.x + offsetX*0.1f, binPos.y);

                // Izbere random teksturo, ter hitrost
                int trashIndex = MathUtils.random(1, 4);
                Texture tex = new Texture("garbage-assets/garbage-pieces/trash-0" + trashIndex + ".png");
                float speed = MathUtils.random(40, 80);

                bin.particles.add(new TrashParticle(tex, start, target, speed));
            }

            for(int i = bin.particles.size-1; i>=0; i--){
                TrashParticle p = bin.particles.get(i);
                if(p.update(Gdx.graphics.getDeltaTime())){
                    bin.particles.removeIndex(i);
                    p.texture.dispose();
                }
            }
        }
    }



    private void updateInfoPanel() {
        if (selectedPOI != null) {
            String typeDisplay = selectedPOI.type;
            infoLabel.setText("Tip: " + typeDisplay + "\nKoordinate: " + String.format("%.6f", selectedPOI.lat) + ", " + String.format("%.6f", selectedPOI.lon));
        } else {
            infoLabel.setText("Selektiran marker");
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
        if (weatherTexture != null) weatherTexture.dispose();
        if (weatherBackgroundTexture != null) weatherBackgroundTexture.dispose();
        if (buttonBin != null) buttonBin.dispose();
        if (buttonEcoIsland != null) buttonEcoIsland.dispose();
        if (buttonDisposalSite != null) buttonDisposalSite.dispose();
        if (buttonHidden != null) buttonHidden.dispose();

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

        if (selectedPOI != null) {
            float pixelX = worldPos.x;
            float pixelY = Constants.MAP_HEIGHT - worldPos.y;

            Geolocation geo = MapRasterTiles.pixelToGeo(pixelX, pixelY, beginTile.x, beginTile.y);

            for (Poi bin : simulatedBins) {
                if (bin.poi == selectedPOI) {
                    bin.poi.lat = geo.lat;
                    bin.poi.lon = geo.lng;
                    sendUpdateLocationRequest(bin);
                    break;
                }
            }

            selectedPOI = null;
            updateInfoPanel();
            return true;
        }

        if (poiObjects != null) {
            for (MapObject poi : poiObjects) {
                if ((poi.type.equals("bin") && binVisible) ||
                    (poi.type.equals("eco-island") && ecoIslandVisible) ||
                    (poi.type.equals("disposal-site") && disposalSiteVisible)) {
                    Vector2 pos = MapRasterTiles.getPixelPositionPrecise(poi.lat, poi.lon, beginTile.x, beginTile.y);
                    float size = markerSizeCurrent;
                    if (worldPos.x >= pos.x - size / 2 && worldPos.x <= pos.x + size / 2 &&
                        worldPos.y >= pos.y - size / 2 && worldPos.y <= pos.y + size / 2) {
                        selectedPOI = poi;
                        updateInfoPanel();
                        return true;
                    }
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

    private void sendUpdateLocationRequest(Poi bin) {
        new Thread(() -> {
            try {
                System.out.println("Updating POI: id=" + bin.poi.id + ", lat=" + bin.poi.lat + ", lon=" + bin.poi.lon);
                MapDataService service = new MapDataService();
                service.updatePoiLocation(bin.poi.id, bin.poi.lat, bin.poi.lon);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
