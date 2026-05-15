# Malaki Android App

Android client for Malaki AI Child Guardian. Collects data from child's device and displays insights to parents.

## Tech Stack

* Kotlin
* Jetpack Compose
* Firebase Authentication
* Firebase Firestore
* Room Database
* OkHttp
* Coroutines

## Setup

1. Clone repository
2. Create secrets.properties in project root:
RAPIDAPI\_KEY=your\_rapidapi\_key
BACKEND\_URL=http://your-pc-ip:8000
3. Add google-services.json (Firebase configuration)
4. Build and run in Android Studio

## Features

### Child App

* Message collection via Accessibility Service
* Music listening tracking
* Journaling with mood tracking
* App usage monitoring
* Browser URL capture

### Parent App

* Real-time grooming alerts
* Wellbeing dashboard
* Music insights
* App usage patterns
* Content safety reports
* Behavioral anomaly detection

## Permissions Required

* Usage Access (for app monitoring)
* Accessibility Service (for message reading)
* Notification Listener (for music detection)
* Internet (for backend sync)

