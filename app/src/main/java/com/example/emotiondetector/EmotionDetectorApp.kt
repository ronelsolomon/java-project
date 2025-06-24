package com.example.emotiondetector

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EmotionDetectorApp : Application()

// Add this to your AndroidManifest.xml:
// <application
//     android:name=".EmotionDetectorApp"
//     ...
// >
