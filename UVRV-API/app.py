from flask import Flask, request, jsonify, send_from_directory
import torch
from torchvision import transforms, models
from PIL import Image
import io
import csv
from datetime import datetime
from pathlib import Path
import threading

app = Flask(__name__, static_folder='static/')

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
    print("Zaganjam Flask storitev na http://localhost:5000")
    app.run(host='localhost', port=5000, debug=True)
