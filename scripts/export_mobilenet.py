"""
Export MobileNetV3-Small to ONNX for Privora on-device photo classification.

Usage:
    pip install torch torchvision onnx onnxruntime
    python scripts/export_mobilenet.py

Output:
    app/src/main/assets/models/mobilenet_v3_small.onnx
    app/src/main/assets/imagenet_labels.txt (1000 labels)
"""

import os
import sys

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    os.environ["PYTHONIOENCODING"] = "utf-8"

import torch
import torch.nn as nn
import torchvision.models as models
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")
MODELS_DIR = os.path.join(ASSETS_DIR, "models")
ONNX_PATH = os.path.join(MODELS_DIR, "mobilenet_v3_small.onnx")
LABELS_PATH = os.path.join(ASSETS_DIR, "imagenet_labels.txt")

os.makedirs(MODELS_DIR, exist_ok=True)

print("Loading MobileNetV3-Small (pretrained on ImageNet)...")
base_model = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
base_model.eval()

# Create a model that outputs BOTH classification logits AND feature embeddings
class MobileNetWithEmbedding(nn.Module):
    def __init__(self, base):
        super().__init__()
        self.features = base.features
        self.avgpool = base.avgpool
        self.classifier = base.classifier

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        embedding = torch.flatten(x, 1)  # 576-dim feature vector
        logits = self.classifier(embedding)  # 1000-class logits
        return logits, embedding

model = MobileNetWithEmbedding(base_model)
model.eval()

dummy_input = torch.randn(1, 3, 224, 224)

print(f"Exporting to ONNX (legacy exporter): {ONNX_PATH}")
# Use legacy exporter explicitly
torch.onnx.export(
    model,
    dummy_input,
    ONNX_PATH,
    opset_version=13,
    input_names=["input"],
    output_names=["logits", "embedding"],
    dynamic_axes={
        "input": {0: "batch_size"},
        "logits": {0: "batch_size"},
        "embedding": {0: "batch_size"},
    },
)

# Verify
import onnxruntime as ort
session = ort.InferenceSession(ONNX_PATH)
for inp in session.get_inputs():
    print(f"  Input: {inp.name} shape={inp.shape}")
for out in session.get_outputs():
    print(f"  Output: {out.name} shape={out.shape}")

test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
logits, embedding = session.run(None, {"input": test_input})
print(f"Logits shape: {logits.shape}, Embedding shape: {embedding.shape}")
print(f"Model size: {os.path.getsize(ONNX_PATH) / 1024 / 1024:.1f} MB")

# Test with a real image from torchvision
from torchvision import transforms
from PIL import Image
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])

# Export ImageNet labels
print(f"Writing ImageNet labels: {LABELS_PATH}")
weights = models.MobileNet_V3_Small_Weights.IMAGENET1K_V1
categories = weights.meta["categories"]
with open(LABELS_PATH, "w") as f:
    for label in categories:
        f.write(label + "\n")

print(f"Done! {len(categories)} labels written.")
print(f"\nFiles created:")
print(f"  {ONNX_PATH}")
print(f"  {LABELS_PATH}")
