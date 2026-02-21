# train.py
import os, shutil, random
import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras import layers, models

# ---------------- FIX: Disable oneDNN warnings ----------------
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

# ---------------- CONFIG ----------------
DATASET_ORIGINAL = "./PlantVillage"   # put PlantVillage dataset here
DATASET_DIR = "./dataset"             # auto-split into train/val/test
IMG_SIZE = 224
BATCH_SIZE = 32
EPOCHS = 10
MODEL_PATH = "plant_disease_model.h5"

# --------- STEP 1: SPLIT DATASET ---------
def create_splits(source_dir, target_dir, train_pct=0.7, val_pct=0.15, test_pct=0.15):
    if os.path.exists(target_dir):
        print(f"[INFO] {target_dir} exists. Skipping split...")
        return
    
    classes = os.listdir(source_dir)
    for cls in classes:
        cls_path = os.path.join(source_dir, cls)
        if not os.path.isdir(cls_path):  # skip if not folder
            continue
        
        images = os.listdir(cls_path)
        random.shuffle(images)

        n_total = len(images)
        n_train = int(train_pct * n_total)
        n_val = int(val_pct * n_total)

        splits = {
            "train": images[:n_train],
            "val": images[n_train:n_train+n_val],
            "test": images[n_train+n_val:]
        }

        for split, files in splits.items():
            out_dir = os.path.join(target_dir, split, cls)
            os.makedirs(out_dir, exist_ok=True)
            for f in files:
                src = os.path.join(cls_path, f)
                if os.path.isfile(src):  # ✅ ensure only files are copied
                    shutil.copy(src, os.path.join(out_dir, f))

create_splits(DATASET_ORIGINAL, DATASET_DIR)

# --------- STEP 2: DATA GENERATORS ---------
train_gen = ImageDataGenerator(
    rescale=1./255,
    rotation_range=20,
    width_shift_range=0.1,
    height_shift_range=0.1,
    shear_range=0.1,
    zoom_range=0.1,
    horizontal_flip=True,
    fill_mode='nearest'
).flow_from_directory(
    os.path.join(DATASET_DIR, "train"),
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical"
)

val_gen = ImageDataGenerator(rescale=1./255).flow_from_directory(
    os.path.join(DATASET_DIR, "val"),
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical"
)

# --------- STEP 3: BUILD CNN MODEL ---------
base_model = MobileNetV2(include_top=False, input_shape=(IMG_SIZE, IMG_SIZE, 3), weights="imagenet")
base_model.trainable = False  # freeze base model

x = layers.GlobalAveragePooling2D()(base_model.output)
x = layers.Dropout(0.3)(x)
x = layers.Dense(256, activation="relu")(x)
x = layers.Dropout(0.3)(x)
output = layers.Dense(train_gen.num_classes, activation="softmax")(x)

model = models.Model(inputs=base_model.input, outputs=output)
model.compile(optimizer=tf.keras.optimizers.Adam(1e-4),
              loss="categorical_crossentropy",
              metrics=["accuracy"])

# --------- STEP 4: TRAIN MODEL ---------
history = model.fit(train_gen, validation_data=val_gen, epochs=EPOCHS)

# --------- STEP 5: SAVE MODEL & LABELS ---------
model.save(MODEL_PATH)
print(f"[INFO] Model saved to {MODEL_PATH}")

# Save labels (class names) for later use
labels = list(train_gen.class_indices.keys())
with open("labels.txt", "w", encoding="utf-8") as f:
    for lbl in labels:
        f.write(lbl + "\n")
print("[INFO] Labels saved to labels.txt")

