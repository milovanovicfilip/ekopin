const mqtt = require("mqtt");
const { MongoClient, ObjectId } = require("mongodb");

const MQTT_URL = process.env.MQTT_URL || "mqtt://localhost:1883";
const MONGO_URL="mongodb+srv://milovanovic8filip:geslo123@cluster0.gsr8kmn.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
const DB_REAL="ekopin_real"
const DB_SIM="ekopin_sim"
const DB_SPLETNO="test"

const CHUNK_SIZE = Number(process.env.CHUNK_SIZE || 200);
const MAX_TAGS = Number(process.env.MAX_TAGS || 2000);
const MAX_TEMPS = Number(process.env.MAX_TEMPS || 5000);
const MAX_GEIGER = Number(process.env.MAX_GEIGER || 5000);
const TEMP_ALERT_TOPIC = (mode) => `ekopin/${mode}/temperature/record`;
const GEIGER_ALERT_TOPIC = (mode) => `ekopin/${mode}/geiger/record`;

const mqttClient = mqtt.connect(MQTT_URL, { reconnectPeriod: 2000 });
const mongoClient = new MongoClient(MONGO_URL);

const dbByMode = { real: null, sim: null };

function parseModeFromTopic(topic) {
  const parts = String(topic || "").split("/");
  return parts.length >= 2 ? parts[1] : null;
}

function safeJsonParse(bufOrString) {
  try {
    if (Buffer.isBuffer(bufOrString)) return JSON.parse(bufOrString.toString("utf8"));
    return JSON.parse(String(bufOrString));
  } catch {
    return null;
  }
}

async function ensureDb(mode) {
  if (!dbByMode[mode]) {
    dbByMode[mode] = mongoClient.db(mode === "real" ? DB_REAL : DB_SIM);
  }
  return dbByMode[mode];
}

function chunkPublish(topicBase, deviceId, reqId, items) {
  const totalChunks = Math.max(1, Math.ceil(items.length / CHUNK_SIZE));
  const topic = `${topicBase}/${deviceId}/${reqId}`;

  for (let i = 0; i < totalChunks; i++) {
    const chunk = items.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE);
    const payload = {
      deviceId,
      reqId,
      chunkIndex: i,
      totalChunks,
      items: chunk,
      ts: Date.now(),
    };
    mqttClient.publish(topic, JSON.stringify(payload), { qos: 1, retain: false });
  }

  console.log("[MQTT OUT]", topic, `chunks=${totalChunks}`, `totalItems=${items.length}`);
}

function normalizeId(input) {
  const s = String(input || "").trim();
  if (!s) return null;
  if (/^[0-9a-fA-F]{24}$/.test(s)) return new ObjectId(s);
  return null;
}

function stringifyMongoId(doc) {
  if (!doc) return doc;
  if (doc._id && typeof doc._id !== "string") doc._id = String(doc._id);
  return doc;
}

async function ensureIndexes(db) {
  await db.collection("pollution_tags").createIndex({ ts: -1 });
  await db.collection("pollution_tags").createIndex({ deviceId: 1, ts: -1 });
  await db.collection("temperature").createIndex({ ts: -1 });
  await db.collection("temperature").createIndex({ deviceId: 1, ts: -1 });
  await db.collection("geiger").createIndex({ ts: -1 });
  await db.collection("geiger").createIndex({ deviceId: 1, ts: -1 });
}

function publishEvent(topic, payload) {
  mqttClient.publish(topic, JSON.stringify(payload), { qos: 1, retain: false });
  console.log("[MQTT OUT]", topic);
}

async function ensureTempStats(db) {
  // one doc per DB (real/sim)
  const col = db.collection("temperature_stats");
  await col.updateOne(
    { _id: "global" },
    { $setOnInsert: { _id: "global", createdAt: new Date() } },
    { upsert: true }
  );
  return col;
}

async function ensureGeigerStats(db) {
  // one doc per DB (real/sim)
  const col = db.collection("geiger_stats");
  await col.updateOne(
    { _id: "global" },
    { $setOnInsert: { _id: "global", createdAt: new Date() } },
    { upsert: true }
  );
  return col;
}

function isNumber(x) {
  return typeof x === "number" && !Number.isNaN(x);
}

async function checkAndPublishTempRecords(db, mode, doc) {
  const value = Number(doc.value);
  if (!isNumber(value)) return;

  const ts = Number(doc.ts || Date.now());
  const deviceId = String(doc.deviceId || "unknown");

  const statsCol = await ensureTempStats(db);
  const stats = await statsCol.findOne({ _id: "global" }) || {};

  const prevMax = stats.maxValue;
  if (!isNumber(prevMax) || value > prevMax) {
    await statsCol.updateOne(
      { _id: "global" },
      {
        $set: {
          maxValue: value,
          maxTs: ts,
          maxDeviceId: deviceId,
          maxUpdatedAt: new Date(),
        },
      }
    );

    publishEvent(TEMP_ALERT_TOPIC(mode), {
      type: "NEW_MAX",
      value,
      prev: isNumber(prevMax) ? prevMax : null,
      ts,
      deviceId,
    });
  }

  const prevMin = stats.minValue;
  if (!isNumber(prevMin) || value < prevMin) {
    await statsCol.updateOne(
      { _id: "global" },
      {
        $set: {
          minValue: value,
          minTs: ts,
          minDeviceId: deviceId,
          minUpdatedAt: new Date(),
        },
      }
    );

    publishEvent(TEMP_ALERT_TOPIC(mode), {
      type: "NEW_MIN",
      value,
      prev: isNumber(prevMin) ? prevMin : null,
      ts,
      deviceId,
    });
  }
}

async function checkAndPublishGeigerRecords(db, mode, doc) {
  const value = Number(doc.value);
  if (!isNumber(value)) return;

  const ts = Number(doc.ts || Date.now());
  const deviceId = String(doc.deviceId || "unknown");

  const statsCol = await ensureGeigerStats(db);
  const stats = await statsCol.findOne({ _id: "global" }) || {};

  const prevMax = stats.maxValue;
  if (!isNumber(prevMax) || value > prevMax) {
    await statsCol.updateOne(
      { _id: "global" },
      {
        $set: {
          maxValue: value,
          maxTs: ts,
          maxDeviceId: deviceId,
          maxUpdatedAt: new Date(),
        },
      }
    );

    publishEvent(GEIGER_ALERT_TOPIC(mode), {
      type: "NEW_MAX",
      value,
      prev: isNumber(prevMax) ? prevMax : null,
      ts,
      deviceId,
    });
  }

  const prevMin = stats.minValue;
  if (!isNumber(prevMin) || value < prevMin) {
    await statsCol.updateOne(
      { _id: "global" },
      {
        $set: {
          minValue: value,
          minTs: ts,
          minDeviceId: deviceId,
          minUpdatedAt: new Date(),
        },
      }
    );

    publishEvent(GEIGER_ALERT_TOPIC(mode), {
      type: "NEW_MIN",
      value,
      prev: isNumber(prevMin) ? prevMin : null,
      ts,
      deviceId,
    });
  }
}

mqttClient.on("connect", async () => {
  console.log("[MQTT] connected", MQTT_URL);

  try {
    await mongoClient.connect();
    console.log("[MongoDB] connected", MONGO_URL);

    const realDb = await ensureDb("real");
    const simDb = await ensureDb("sim");
    await ensureIndexes(realDb);
    await ensureIndexes(simDb);
    console.log("[MongoDB] indexes ensured");
  } catch (e) {
    console.error("[MongoDB] connect/index error", e);
    return;
  }

  mqttClient.subscribe("ekopin/+/pollution_tags/add", { qos: 1 });
  mqttClient.subscribe("ekopin/+/pollution_tags/list/request", { qos: 1 });
  mqttClient.subscribe("ekopin/+/pollution_tags/delete", { qos: 1 });

  mqttClient.subscribe("ekopin/+/temperature/add", { qos: 1 });
  mqttClient.subscribe("ekopin/+/temperature/list/request", { qos: 1 });

  mqttClient.subscribe("ekopin/+/geiger/add", { qos: 1 });
  mqttClient.subscribe("ekopin/+/geiger/list/request", { qos: 1 });

  console.log("[MQTT] Subscribed to topics");
});

mqttClient.on("message", async (topic, message) => {
  const text = message?.toString("utf8") ?? "";
  console.log("[BACKEND IN]", topic, text);

  const mode = parseModeFromTopic(topic);
  if (mode !== "real" && mode !== "sim") return;

  try {
    const db = await ensureDb(mode);

    if (topic.endsWith("/pollution_tags/add")) {
      const tag = safeJsonParse(message) || {};
      tag.mode = tag.mode || mode;
      tag.ts = Number(tag.ts || Date.now());
      tag.deviceId = String(tag.deviceId || "unknown");
      tag.label = String(tag.label || "").trim();
      tag.description = String(tag.description || "");
 
      const sevRaw = (tag.severity ?? "").toString().trim();
      if (!sevRaw || sevRaw.toLowerCase() === "null") {
         tag.severity = "Nizka";
      } else {

      const s = sevRaw.toLowerCase();
      if (s.startsWith("niz")) tag.severity = "Nizka";
      else if (s.startsWith("sred")) tag.severity = "Srednja";
      else if (s.startsWith("vis")) tag.severity = "Visoka";
      else tag.severity = sevRaw;
      }

      if (tag.lat !== undefined) tag.lat = Number(tag.lat);
      if (tag.lng !== undefined) tag.lng = Number(tag.lng);

      if (!tag.label) {
        publishEvent(`ekopin/${mode}/pollution_tags/add_error`, {
          ok: false,
          reason: "Missing label",
          ts: Date.now(),
        });
        return;
      }

      const res = await db.collection("pollution_tags").insertOne(tag);
      const out = { ...tag, _id: String(res.insertedId) };

      publishEvent(`ekopin/${mode}/pollution_tags/added`, out);
      return;
    }

    if (topic.endsWith("/pollution_tags/list/request")) {
      const req = safeJsonParse(message) || {};
      const deviceId = String(req.deviceId || "unknown");
      const reqId = String(req.reqId || Date.now());

      console.log(`[POLLUTION_TAGS] List request for mode=${mode}, deviceId=${deviceId}, db=${mode === "real" ? DB_REAL : DB_SIM}`);

      const filter = {};

      const docs = await db
        .collection("pollution_tags")
        .find(filter)
        .sort({ ts: -1 })
        .limit(MAX_TAGS)
        .toArray();

      console.log(`[POLLUTION_TAGS] Found ${docs.length} tags in ${mode} database`);
      docs.forEach((doc, idx) => {
        console.log(`  Tag ${idx}: id=${doc._id}, lat=${doc.lat}, lng=${doc.lng}, mode=${doc.mode}`);
      });

      docs.forEach(stringifyMongoId);

      const topicBase = `ekopin/${mode}/pollution_tags/list/response`;
      chunkPublish(topicBase, deviceId, reqId, docs);
      return;
    }

    if (topic.endsWith("/pollution_tags/delete")) {
      const req = safeJsonParse(message) || {};
      const requesterDeviceId = String(req.deviceId || "unknown");
      const idStr = req.id || req._id;
      const oid = normalizeId(idStr);

      if (!oid) {
        publishEvent(`ekopin/${mode}/pollution_tags/deleted`, {
          ok: false,
          id: String(idStr || ""),
          reason: "Invalid id",
          ts: Date.now(),
        });
        return;
      }

      const existing = await db.collection("pollution_tags").findOne({ _id: oid });

      if (!existing) {
        publishEvent(`ekopin/${mode}/pollution_tags/deleted`, {
          ok: false,
          id: String(idStr),
          reason: "Not found",
          ts: Date.now(),
        });
        return;
      }

      const owner = String(existing.deviceId || "unknown");
      if (owner !== requesterDeviceId) {
        publishEvent(`ekopin/${mode}/pollution_tags/deleted`, {
          ok: false,
          id: String(idStr),
          reason: "Not owner",
          ts: Date.now(),
        });
        return;
      }

      await db.collection("pollution_tags").deleteOne({ _id: oid });

      publishEvent(`ekopin/${mode}/pollution_tags/deleted`, {
        ok: true,
        id: String(idStr),
        deviceId: requesterDeviceId,
        ts: Date.now(),
      });
      return;
    }

    if (topic.endsWith("/temperature/add")) {
      const data = safeJsonParse(message);
      if (!data) {
        console.warn("[TEMP] Invalid JSON");
        return;
      }

      const doc = {
        ...data,
        mode,
        ts: Number(data.ts || Date.now()),
        deviceId: String(data.deviceId || "unknown"),
        createdAt: new Date(),
      };

      const result = await db.collection("temperature").insertOne(doc);
      doc._id = String(result.insertedId);

      await checkAndPublishTempRecords(db, mode, doc);
      publishEvent(`ekopin/${mode}/temperature/added`, doc);
      return;
    }

    if (topic.endsWith("/temperature/list/request")) {
      const req = safeJsonParse(message) || {};
      const deviceId = String(req.deviceId || "unknown");
      const reqId = String(req.reqId || Date.now());

      const docs = await db
        .collection("temperature")
        .find({})
        .sort({ ts: -1 })
        .limit(MAX_TEMPS)
        .toArray();

      docs.forEach(stringifyMongoId);

      const topicBase = `ekopin/${mode}/temperature/list/response`;
      chunkPublish(topicBase, deviceId, reqId, docs);
      return;
    }

    if (topic.endsWith("/geiger/add")) {
      const data = safeJsonParse(message);
      if (!data) {
        console.warn("[GEIGER] Invalid JSON");
        return;
      }

      const doc = {
        ...data,
        mode,
        ts: Number(data.ts || Date.now()),
        deviceId: String(data.deviceId || "unknown"),
        createdAt: new Date(),
      };

      const result = await db.collection("geiger").insertOne(doc);
      doc._id = String(result.insertedId);

      await checkAndPublishGeigerRecords(db, mode, doc);
      publishEvent(`ekopin/${mode}/geiger/added`, doc);
      return;
    }

    if (topic.endsWith("/geiger/list/request")) {
      const req = safeJsonParse(message) || {};
      const deviceId = String(req.deviceId || "unknown");
      const reqId = String(req.reqId || Date.now());

      const docs = await db
        .collection("geiger")
        .find({})
        .sort({ ts: -1 })
        .limit(MAX_GEIGER)
        .toArray();

      docs.forEach(stringifyMongoId);

      const topicBase = `ekopin/${mode}/geiger/list/response`;
      chunkPublish(topicBase, deviceId, reqId, docs);
      return;
    }
  } catch (e) {
    console.error("[BACKEND ERROR]", e);
  }
});

mqttClient.on("error", (e) => console.error("[MQTT] error", e));
process.on("unhandledRejection", (e) => console.error("[NODE] unhandledRejection", e));
process.on("uncaughtException", (e) => console.error("[NODE] uncaughtException", e));
