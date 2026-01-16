import torch
import torch.nn as nn
from torchvision import datasets, transforms, models
from torch.utils.data import DataLoader, random_split
from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score, accuracy_score
import numpy as np

# Nastavitve
DATA_DIR = "data-processed"
BATCH_SIZE = 16

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Transformacije
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
])

# Dataset + split
dataset = datasets.ImageFolder(DATA_DIR, transform=transform)

train_size = int(0.8 * len(dataset))
val_size = len(dataset) - train_size
_, val_dataset = random_split(dataset, [train_size, val_size])

val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

# Nalaganje modela
model = models.resnet18(weights=None)
model.fc = nn.Linear(model.fc.in_features, 1)

model.load_state_dict(torch.load("model.pth", map_location=device))
model.to(device)
model.eval()

# Evalvacija
y_true = []
y_pred = []

with torch.no_grad():
    for images, labels in val_loader:
        images = images.to(device)
        labels = labels.to(device)

        outputs = model(images)
        probs = torch.sigmoid(outputs)
        preds = (probs >= 0.5).int().squeeze()

        y_true.extend(labels.cpu().numpy())
        y_pred.extend(preds.cpu().numpy())

# Metrike
acc = accuracy_score(y_true, y_pred)
prec = precision_score(y_true, y_pred)
rec = recall_score(y_true, y_pred)
f1 = f1_score(y_true, y_pred)

cm = confusion_matrix(y_true, y_pred)
tn, fp, fn, tp = cm.ravel()

print("\n=== REZULTATI EVALVACIJE ===")
print(f"Accuracy : {acc:.4f}")
print(f"Precision: {prec:.4f}")
print(f"Recall   : {rec:.4f}")
print(f"F1-score : {f1:.4f}")

print("\nConfusion Matrix:")
print(f"TP: {tp}  FP: {fp}")
print(f"FN: {fn}  TN: {tn}")
