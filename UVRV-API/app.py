from bson import ObjectId
from flask import Flask, request, jsonify, send_from_directory
import torch
from torchvision import transforms, models
from PIL import Image
import io
import csv
from datetime import datetime
from pathlib import Path
import threading
from pymongo import MongoClient

app = Flask(__name__, static_folder='static/')

client = MongoClient("mongodb+srv://milovanovic8filip:geslo123@cluster0.gsr8kmn.mongodb.net/test")
db = client["test"]

poi_col = db["pois"]
bin_status_col = db["bin_status"]


CSV_PATH = Path('results.csv')
csv_lock = threading.Lock()

if not CSV_PATH.exists():
    with open(CSV_PATH, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['cas', 'probability', 'status', 'image_name'])

model = models.resnet18(weights=None)
model.fc = torch.nn.Linear(model.fc.in_features, 1)
model.load_state_dict(torch.load("model/model.pth", map_location="cpu"))
model.eval()

transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
])

def zapisi_v_csv(cas, probability, status, image_name):
    with csv_lock:
        with open(CSV_PATH, 'a', newline='') as f:
            writer = csv.writer(f)
            writer.writerow([cas, probability, status, image_name])

# ============================================ CRUD ============================================

# CREATE/DELETE STATUS
# (za posodobitev tak moremo novo analizo delat in ne moremo spreminjat samo enga parametra)
# (rajsi vse to damo v en endpoint, ker bi v nasportnem meli prevec ponavljajoce kode)
@app.route('/analiziraj_in_shrani', methods=['POST'])
def analiziraj_in_shrani():
    if "slika" not in request.files:
        return jsonify({"error": "Manjkajoča fotografija"}), 400

    poi_id = request.form.get("poi_id")
    if not poi_id:
        return jsonify({"error": "Manjkajoči POI ID"}), 400

    try:
        poi_obj_id = ObjectId(poi_id)
    except:
        return jsonify({"error": "Nevaljeven POI ID"}), 400

    poi = poi_col.find_one({"_id": poi_obj_id})
    if not poi:
        return jsonify({"error": "POI ni najden"}), 404

    if poi.get("type") != "bin":
        return jsonify({"error": "POI ni tipa 'bin'"})

    poi_status = bin_status_col.find_one({"poiId": poi_obj_id})
    if poi_status:
        bin_status_col.delete_one({"poiId": poi_obj_id})

    image_bytes = request.files["slika"].read()
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

    img_tensor = transform(image).unsqueeze(0)

    with torch.no_grad():
        output = model(img_tensor)
        prob = torch.sigmoid(output).item()

        status = "full" if prob >= 0.5 else "empty"

        result = {
            "poiId": poi_obj_id,
            "probability": prob,
            "status": status,
            "createdAt": datetime.utcnow()
        }

        bin_status_col.insert_one(result)

        return jsonify({
            "poi_id": poi_id,
            "probability": round(prob, 4),
            "status": status,
            "message": "Analiza uspešno shranjena"
        })

# READ (GET) STATUS
@app.route("/bin_status/<poi_id>")
def get_bin_status(poi_id):
    try:
        poi_obj_id = ObjectId(poi_id)
    except:
        return jsonify({"error": "Neveljaven ObjectId"}), 400

    poi = db.pois.find_one({"_id": poi_obj_id})
    if not poi:
        return jsonify({"error": "POI ni najden"}), 404

    if poi.get("type") != "bin":
        return jsonify({"error": "POI ni tipa 'bin'"}), 400

    status = db.bin_status.find_one(
        {"poiId": poi_obj_id},
        sort=[("createdAt", -1)]
    )

    if not status:
        return jsonify({"error": "Ni statusa za ta smetnjak"}), 404

    return jsonify({
        "poiId": str(poi_obj_id),
        "probability": status["probability"],
        "status": status["status"],
        "createdAt": status["createdAt"]
    })

# DELETE
@app.route('/bin_status/<poi_id>', methods=['DELETE'])
def delete_bin_status(poi_id):
    try:
        poi_obj_id = ObjectId(poi_id)
    except:
        return jsonify({"error": "Neveljaven ObjectId"}), 400

    poi = db.pois.find_one({"_id": poi_obj_id})
    if not poi:
        return jsonify({"error": "POI ni najden"}), 404

    if poi.get("type") != "bin":
        return jsonify({"error": "POI ni tipa 'bin'"}), 400

    status = db.bin_status.find_one(
        {"poiId": poi_obj_id},
        sort=[("createdAt", -1)]
    )

    if not status:
        return jsonify({"error": "Ni statusa za ta smetnjak"}), 404

    bin_status_col.delete_one({"poiId": poi_obj_id})

    return jsonify({"success": "Brisanje uspešno!"}), 200

# ==============================================================================================

@app.route('/')
def index():
    return send_from_directory('static/', 'index.html')

@app.route('/analiziraj', methods=['POST'])
def analiziraj():
    if 'slika' not in request.files:
        return jsonify({'napaka': 'Manjka polje "slika"'}), 400

    slika_file = request.files['slika']
    if slika_file.filename == '':
        return jsonify({'napaka': 'Prazno ime datoteke'}), 400

    cas = request.form.get('cas') or datetime.now().isoformat()

    try:
        slika_bytes = slika_file.read()
        slika_pil = Image.open(io.BytesIO(slika_bytes)).convert("RGB")
        img_tensor = transform(slika_pil).unsqueeze(0)

        with torch.no_grad():
            output = model(img_tensor)
            prob = torch.sigmoid(output).item()
            status = "full" if prob >= 0.5 else "empty"

        zapisi_v_csv(cas, round(prob, 4), status, slika_file.filename)

        return jsonify({
            'cas': cas,
            'probability': round(prob, 4),
            'status': status,
            'image_name': slika_file.filename,
            'status_code': 'uspeh'
        })

    except Exception as e:
        return jsonify({'napaka': str(e), 'status_code': 'napaka'}), 500

@app.route('/zgodovina', methods=['GET'])
def zgodovina():
    try:
        with csv_lock:
            with open(CSV_PATH, 'r') as f:
                reader = csv.DictReader(f)
                rezultati = list(reader)
        return jsonify({'stevilo_rezultatov': len(rezultati), 'rezultati': rezultati, 'status': 'uspeh'})
    except Exception as e:
        return jsonify({'napaka': str(e), 'status': 'napaka'}), 500

if __name__ == '__main__':
    print("Zaganjam Flask storitev na http://localhost:8000")
    app.run(host='localhost', port=8000, debug=True)
