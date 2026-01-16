import os
from PIL import Image
from torchvision import transforms


# Poti
original_path = "data-all"
processed_root = "data-processed"

# Transformacije
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225]
    )
])

# Razdelitev in shranjevanje
for img_name in os.listdir(original_path):
    img_path = os.path.join(original_path, img_name)
    if not img_name.lower().endswith((".png", ".jpg", ".jpeg")):
        continue

    img = Image.open(img_path).convert("RGB")

    img_tensor = transform(img)
    img_processed = transforms.ToPILImage()(img_tensor)

    if img_name.startswith("0-"):
        label = "0"
    elif img_name.startswith("1-"):
        label = "1"
    else:
        continue

    save_dir = os.path.join(processed_root, label)
    os.makedirs(save_dir, exist_ok=True)

    img_processed.save(os.path.join(save_dir, img_name))

print("Predobdelava in razdelitev končana!")
