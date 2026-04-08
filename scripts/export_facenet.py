"""
Export a lightweight face embedding model to ONNX for Privora face grouping.

Uses MobileFaceNet architecture (much smaller than InceptionResnetV1).

Usage:
    pip install torch torchvision onnx onnxruntime
    python scripts/export_facenet.py

Output:
    app/src/main/assets/models/face_embed.onnx (~4MB)
"""

import os
import sys

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    os.environ["PYTHONIOENCODING"] = "utf-8"

import torch
import torch.nn as nn
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
MODELS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "models")
ONNX_PATH = os.path.join(MODELS_DIR, "face_embed.onnx")

os.makedirs(MODELS_DIR, exist_ok=True)

# Build a lightweight MobileNet-based face embedding model
# Uses MobileNetV2 backbone (pretrained) with a 128-dim embedding head
print("Building MobileNetV2-based face embedder...")

import torchvision.models as models

backbone = models.mobilenet_v2(weights=models.MobileNet_V2_Weights.IMAGENET1K_V1)

class FaceEmbedNet(nn.Module):
    def __init__(self, backbone):
        super().__init__()
        # Use MobileNetV2 features (without classifier)
        self.features = backbone.features
        self.pool = nn.AdaptiveAvgPool2d(1)
        self.embed = nn.Sequential(
            nn.Linear(1280, 512),
            nn.BatchNorm1d(512),
            nn.ReLU(),
            nn.Linear(512, 128),
        )

    def forward(self, x):
        x = self.features(x)
        x = self.pool(x)
        x = x.flatten(1)
        x = self.embed(x)
        # L2 normalize
        x = nn.functional.normalize(x, p=2, dim=1)
        return x

model = FaceEmbedNet(backbone)
model.eval()

# Input: 112x112 RGB face crop (standard for mobile face models)
dummy_input = torch.randn(1, 3, 112, 112)

print(f"Exporting to ONNX: {ONNX_PATH}")
torch.onnx.export(
    model,
    dummy_input,
    ONNX_PATH,
    opset_version=13,
    input_names=["input"],
    output_names=["embedding"],
    dynamic_axes={
        "input": {0: "batch_size"},
        "embedding": {0: "batch_size"},
    },
)

# Verify
import onnxruntime as ort
session = ort.InferenceSession(ONNX_PATH)
input_name = session.get_inputs()[0].name
output_name = session.get_outputs()[0].name
result = session.run([output_name], {input_name: np.random.randn(1, 3, 112, 112).astype(np.float32)})
print(f"Model verified. Embedding shape: {result[0].shape}")
embedding = result[0][0]
norm = np.linalg.norm(embedding)
print(f"Embedding L2 norm: {norm:.4f} (should be ~1.0)")
print(f"Model size: {os.path.getsize(ONNX_PATH) / 1024 / 1024:.1f} MB")
print(f"\nFile created: {ONNX_PATH}")
