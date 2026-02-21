🌿 Plant Disease Detection using CNN (MobileNetV2)

PlantVillage based deep learning project for detecting plant leaf diseases using Transfer Learning (MobileNetV2) with real-time camera support and Tamil language output.

📌 Project Overview

This system detects plant leaf diseases using:

📷 Image prediction mode

🎥 Real-time camera detection mode

🧠 CNN model with Transfer Learning

🌱 Advanced leaf detection using HSV color filtering

🚫 Face detection to prevent false positives

🌍 Tamil translation of predicted disease labels

The model classifies plant leaves into disease categories and shows confidence scores.

🧠 Model Architecture

Transfer Learning using:

MobileNetV2 (Pretrained on ImageNet)

Architecture Flow:

Base Model: MobileNetV2 (frozen)

GlobalAveragePooling2D

Dense (256 units, ReLU)

Dropout (0.3)

Output Layer (Softmax)

Image Size: 224x224 Optimizer: Adam (1e-4) Loss: Categorical Crossentropy

📂 Dataset

Dataset Used: PlantVillage

Multiple plant disease classes

Healthy & infected leaf images

Automatically split into:

70% Train

15% Validation

15% Test

Your train.py script automatically:

Splits dataset

Applies augmentation

Trains model

Saves .h5 model

Generates labels.txt

⚙️ Technologies Used

Python

TensorFlow / Keras

OpenCV

NumPy

Deep Translator (GoogleTranslator)

MobileNetV2 (Transfer Learning)

🚀 Features ✅ Image Prediction Mode

Load image

Preprocess

Predict disease

Translate result to Tamil

Show confidence %

✅ Real-Time Camera Mode

Draws center guide box

Detects leaf using:

HSV green masking

Morphological filtering

Contour detection

Solidity & circularity filtering

Rejects faces using Haar Cascade

Only predicts if confidence > 70%

Auto timeout after 15 seconds

plant-disease-detection/ │ ├── PlantVillage/ # Original dataset ├── dataset/ # Auto-split dataset │ ├── train/ │ ├── val/ │ └── test/ │ ├── train.py ├── main.py # Camera + Image prediction script ├── plant_disease_model.h5 ├── labels.txt └── README.md
