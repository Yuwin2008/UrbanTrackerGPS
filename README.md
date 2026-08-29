# 🛰️ UrbanTrackerGPS

### Android GPS Camera Location Client

**UrbanTrackerGPS** is a Kotlin-based Android application designed to provide the real-world GPS location of a smartphone being used as a camera node in a vehicle-tracking system.

The application obtains the phone's **latitude, longitude, and timestamp**, then communicates this location to a tracking server. The vehicle itself does not need a GPS device. When a vehicle passes through the camera's field of view, its observation can be associated with the camera's geographical location.

---

## 🚀 Features

* 📍 Real-time GPS location of the camera device
* 📱 Android smartphone camera node
* 🟣 Built with Kotlin
* 📡 Sends camera coordinates to a server
* 🕒 Timestamped location updates
* 🔄 Continuous GPS updates
* 🚗 Provides location context for vehicles passing the camera
* 📡 Suitable for multi-camera vehicle tracking systems

---

## 🏗️ System Architecture

```text
             📱 Android Camera App
                       │
                       │ GPS
                       ▼
                📍 Camera Location
                 Latitude / Longitude
                       │
                       │ Network
                       ▼
                 🖥️ Tracking Server
```

> **Important:** UrbanTrackerGPS tracks the **camera's location**, not the vehicle's GPS location.

---

## ⚙️ How It Works

### 1. 📱 Start the Android App

The Android phone is used as a camera node. The app runs alongside the camera system and obtains the device's GPS position.

### 2. 📍 Get Camera Coordinates

The application reads the device's current GPS coordinates:

```text
Latitude
Longitude
Timestamp
```

For example:

```json
{
    "latitude": 12.9716,
    "longitude": 77.5946,
    "timestamp": "2026-08-29T12:30:00"
}
```

These coordinates represent the **camera's physical location**.

### 3. 📡 Send Location to the Server

The app communicates with the tracking server and sends the current camera coordinates.

### 4. 🚗 Vehicle Passes the Camera

When a vehicle passes through the camera's field of view, the vehicle detection/ANPR system can associate that observation with the camera's location and timestamp.

For example:

```text
Camera: CAM_01
Location: 12.9716, 77.5946
Time:     12:30:05

            ↓

Vehicle observed by CAM_01
```

### 5. 🌆 Multi-Camera Tracking

When multiple camera devices are deployed at different locations, observations from those cameras can be combined to reconstruct the movement of a vehicle through the monitored area.

```text
📍 CAM 01 ─────► 📍 CAM 02 ─────► 📍 CAM 03
   🚗               🚗               🚗
  10:30            10:34            10:39
```

---

## 🧠 Core Concept

The application provides a **geographical anchor for each camera**.

```text
Camera GPS
     +
Vehicle Observation
     +
Timestamp
     ↓
Geographical Vehicle Observation
```

The vehicle does not need to transmit its own GPS coordinates. Its observed location is derived from the camera that detected it.

---

## 📂 Project Structure

```text
UrbanTrackerGPS/
│
├── app/                    # Android application
│   ├── src/
│   └── ...
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

The repository contains the **Android/Kotlin application** responsible for obtaining and transmitting the camera device's location.

---

## 🛠️ Technologies

| Technology               | Purpose                         |
| ------------------------ | ------------------------------- |
| 🟣 Kotlin                | Android application development |
| 🤖 Android               | Camera/GPS device platform      |
| 📍 GPS                   | Camera positioning              |
| 📡 Network Communication | Sending location to server      |
| 📱 Smartphone            | Camera and GPS source           |

---

## ▶️ Running the App

### 1. Clone the repository

```bash
git clone https://github.com/Yuwin2008/UrbanTrackerGPS.git
cd UrbanTrackerGPS
```

### 2. Open in Android Studio

Open the cloned project in **Android Studio** and allow Gradle to synchronize.

### 3. Connect an Android Device

Connect an Android phone through USB debugging or use a compatible emulator.

### 4. Grant Location Permission

Allow the application to access the device's location when prompted.

### 5. Configure the Server

Set the tracking server address according to the server configuration used by your vehicle-tracking system.

### 6. Run

Build and launch the application. The phone can then provide its current GPS coordinates to the connected server.

---

## 📡 Example Location Data

The camera location can be represented as:

```json
{
    "latitude": 12.9716,
    "longitude": 77.5946,
    "timestamp": "2026-08-29T12:30:00"
}
```

This data identifies **where the camera was located and when the location was recorded**.

---

## 🌆 Multi-Camera Vehicle Tracking

UrbanTrackerGPS can be used as the GPS layer for a larger multi-camera tracking platform.

```text
             Camera Network

        📱 CAM 01      📱 CAM 02      📱 CAM 03
             │              │              │
             ▼              ▼              ▼
          GPS Location   GPS Location   GPS Location
             │              │              │
             └──────────────┼──────────────┘
                            ▼
                    🖥️ Tracking Server
```

When combined with vehicle detection, ANPR, or vehicle re-identification, the camera locations can provide the geographical points needed to build a vehicle trajectory.

---

## 🎯 Use Cases

* 🚗 Multi-camera vehicle tracking
* 🏙️ Smart-city traffic monitoring
* 🛣️ Urban traffic analytics
* 📍 Camera geolocation
* 🚘 Vehicle trajectory reconstruction
* 📡 Distributed camera networks
* 🤖 AI-powered ANPR systems
* 🚦 Intelligent transportation systems

---

## 🔮 Future Improvements

* 📡 Multi-camera device management
* 🆔 Unique camera identification
* 🔋 Battery-efficient background location updates
* 📶 Improved network reliability
* 🚗 Direct integration with ANPR systems
* 🤖 Vehicle re-identification integration
* 🛣️ Automated trajectory generation
* 📊 Location and traffic analytics

---

## 👨‍💻 Author

**Yuwin2008**

---

## ⭐ Support

If you find the project useful, consider giving the repository a ⭐ on GitHub.
