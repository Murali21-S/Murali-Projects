import cv2, os, numpy as np, time
from tensorflow.keras.models import load_model
from deep_translator import GoogleTranslator   # ✅ stable translator

# Load face cascade for face detection
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

# ---------------- CONFIG ----------------
MODEL_PATH = "plant_disease_model.h5"
LABELS_PATH = "labels.txt"
IMG_SIZE = 224

# Load model & labels
model = load_model(MODEL_PATH)
with open(LABELS_PATH, "r", encoding="utf-8") as f:
    labels = [line.strip() for line in f]

print("[INFO] Model and labels loaded successfully!")

# Translate function
def translate_label(label, target_lang="ta"):
    try:
        return GoogleTranslator(source="en", target=target_lang).translate(label)
    except:
        return label

# ---------- IMAGE PREDICTION ----------
def predict_image(img_path):
    img = cv2.imread(img_path)
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img = cv2.resize(img, (IMG_SIZE, IMG_SIZE))
    img = img.astype("float32") / 255.0
    img = np.expand_dims(img, 0)

    pred = model.predict(img)[0]
    idx = np.argmax(pred)
    label = labels[idx]
    tamil_label = translate_label(label)
    confidence = pred[idx]

    print(f"📷 படம்: {os.path.basename(img_path)}")
    print(f"🔎 முன்னறிவு: {tamil_label} ({confidence*100:.2f}%)")
    return tamil_label, confidence

# ---------- REAL-TIME CAMERA ----------
def predict_camera():
    cap = cv2.VideoCapture(0)
    print("[INFO] Starting camera... Place leaf inside the square.")

    start_time = time.time()
    leaf_detected = False

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        h, w, _ = frame.shape
        box_size = min(h, w) // 2
        x1, y1 = (w - box_size) // 2, (h - box_size) // 2
        x2, y2 = x1 + box_size, y1 + box_size

        # ✅ Always draw center square (guideline)
        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.putText(frame, "Place leaf inside square", (20, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)

        # Crop ROI inside square
        roi = frame[y1:y2, x1:x2]

        # ---- Enhanced Leaf Detection ----
        hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
        
        # More specific green color ranges for leaves
        lower_green1 = np.array([35, 50, 50])   # darker green
        upper_green1 = np.array([85, 255, 255]) # bright green
        lower_green2 = np.array([25, 40, 40])   # light green
        upper_green2 = np.array([35, 255, 255]) # medium green
        
        # Create masks for different green ranges
        mask1 = cv2.inRange(hsv, lower_green1, upper_green1)
        mask2 = cv2.inRange(hsv, lower_green2, upper_green2)
        mask = cv2.bitwise_or(mask1, mask2)
        
        # Remove noise
        kernel = np.ones((5,5), np.uint8)
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)

        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        if contours:
            c = max(contours, key=cv2.contourArea)
            area = cv2.contourArea(c)
            hull_area = cv2.contourArea(cv2.convexHull(c))
            solidity = area / float(hull_area + 1e-6)
            
            # Calculate aspect ratio and other shape features
            x, y, w, h = cv2.boundingRect(c)
            aspect_ratio = float(w) / h
            
            # Calculate perimeter and circularity
            perimeter = cv2.arcLength(c, True)
            circularity = 4 * np.pi * area / (perimeter * perimeter + 1e-6)
            
            # Calculate green ratio more accurately
            green_ratio = cv2.countNonZero(mask) / (roi.size / 3)
            
            # Check for faces in the ROI to avoid false positives
            gray_roi = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray_roi, 1.1, 4)
            
            # If a face is detected, skip this detection
            if len(faces) > 0:
                cv2.putText(frame, "Face Detected - Not a Leaf", (20, 80),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
                continue
            
            # ✅ Much stricter conditions for leaf detection
            # Leaves should have: good green ratio, reasonable aspect ratio, 
            # not too circular (faces are more circular), and good solidity
            if (area > 12000 and 
                green_ratio > 0.25 and 
                0.5 < solidity < 1.0 and
                0.3 < aspect_ratio < 3.0 and  # leaves are not too elongated or square
                circularity < 0.8 and  # leaves are less circular than faces
                area < (roi.shape[0] * roi.shape[1] * 0.8)):  # not too large
                # ✅ Draw tracking box around detected leaf (relative to ROI)
                (lx, ly, lw, lh) = cv2.boundingRect(c)
                cv2.rectangle(frame, (x1 + lx, y1 + ly), (x1 + lx + lw, y1 + ly + lh), (0, 255, 255), 2)
                cv2.putText(frame, "Leaf Detected", (x1 + lx, y1 + ly - 10),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)

                leaf_detected = True

                # Preprocess ROI
                img = cv2.resize(roi, (IMG_SIZE, IMG_SIZE))
                img = img.astype("float32") / 255.0
                img = np.expand_dims(img, 0)

                # Predict
                pred = model.predict(img)[0]
                idx = np.argmax(pred)
                label = labels[idx]
                tamil_label = translate_label(label)
                confidence = pred[idx]

                # Only proceed if confidence is high enough
                if confidence > 0.7:  # 70% confidence threshold
                    print("\n[RESULT] Plant Disease Detection Complete")
                    print(f"🔎 Disease: {tamil_label}")
                    print(f"📊 Confidence: {confidence*100:.2f}%")
                    
                    # Show result on screen for 3 seconds
                    cv2.putText(frame, f"Result: {tamil_label}", (20, 120),
                                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                    cv2.putText(frame, f"Confidence: {confidence*100:.1f}%", (20, 160),
                                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                    cv2.imshow("Plant Disease Detection - Live", frame)
                    cv2.waitKey(3000)  # Show result for 3 seconds
                    
                    cap.release()
                    cv2.destroyAllWindows()
                    return
                else:
                    # Low confidence - show warning
                    cv2.putText(frame, "Low Confidence - Try Again", (20, 120),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 165, 255), 2)
                    print(f"[WARNING] Low confidence ({confidence*100:.1f}%) - detection may be unreliable")

        # Show live feed
        cv2.imshow("Plant Disease Detection - Live", frame)

        # Timeout if no leaf detected
        if not leaf_detected and (time.time() - start_time > 15):
            print("\n[RESULT] No leaf detected within 15 seconds. Closing camera.")
            cap.release()
            cv2.destroyAllWindows()
            return

        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    cap.release()
    cv2.destroyAllWindows()

# ---------------- MAIN ----------------
if __name__ == "__main__":
    mode = input("Enter mode: (1 = Image, 2 = Camera): ")

    if mode == "1":
        test_img = input("Enter image path: ")
        predict_image(test_img)
    elif mode == "2":
        predict_camera()
    else:
        print("[ERROR] Invalid choice. Use 1 or 2.")
