package com.example.emotiondetector.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Utility class for handling runtime permissions
 */
object PermissionUtils {
    // Common permission groups
    object Permissions {
        val CAMERA = arrayOf(Manifest.permission.CAMERA)
        val STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val LOCATION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    
    /**
     * Check if all permissions in the given array are granted
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if the permission rationale should be shown
     */
    fun shouldShowRequestPermissionRationale(activity: Activity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }
    
    /**
     * Open app settings to allow the user to grant permissions manually
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Composable function to request a single permission
     */
    @Composable
    fun RequestPermission(
        permission: String,
        onPermissionDenied: () -> Unit = {},
        onPermissionGranted: () -> Unit = {},
        content: @Composable (PermissionState) -> Unit
    ) {
        val permissionState = rememberPermissionState(permission) { isGranted ->
            if (isGranted) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }
        
        // Request permission when the composable is first composed
        SideEffect {
            permissionState.launchPermissionRequest()
        }
        
        content(permissionState)
    }
    
    /**
     * Composable function to request multiple permissions
     */
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun RequestMultiplePermissions(
        permissions: List<String>,
        onPermissionsDenied: (List<String>) -> Unit = {},
        onPermissionsGranted: () -> Unit = {},
        content: @Composable (MultiplePermissionsState) -> Unit
    ) {
        val permissionsState = rememberMultiplePermissionsState(permissions)
        
        // Request permissions when the composable is first composed
        SideEffect {
            if (!permissionsState.allPermissionsGranted) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
        
        // Handle permission results
        SideEffect {
            if (permissionsState.allPermissionsGranted) {
                onPermissionsGranted()
            } else if (permissionsState.shouldShowRationale) {
                val deniedPermissions = permissionsState.permissions
                    .filter { !it.status.isGranted }
                    .map { it.permission }
                onPermissionsDenied(deniedPermissions)
            }
        }
        
        content(permissionsState)
    }
    
    /**
     * Composable function to handle camera permission
     */
    @Composable
    fun CameraPermission(
        onPermissionGranted: @Composable () -> Unit,
        onPermissionDenied: @Composable () -> Unit,
        onPermissionRationale: @Composable (onRequestPermission: () -> Unit) -> Unit = { it() }
    ) {
        val context = LocalContext.current
        val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
        
        when {
            cameraPermissionState.hasPermission -> {
                onPermissionGranted()
            }
            cameraPermissionState.shouldShowRationale -> {
                onPermissionRationale {
                    cameraPermissionState.launchPermissionRequest()
                }
            }
            else -> {
                onPermissionDenied()
            }
        }
    }
    
    /**
     * Extension function to check if all permissions are granted
     */
    fun MultiplePermissionsState.hasAllPermissionsGranted(): Boolean {
        return permissions.all { it.status.isGranted }
    }
    
    /**
     * Extension function to check if any permission should show rationale
     */
    fun MultiplePermissionsState.shouldShowRationale(): Boolean {
        return permissions.any { it.status.shouldShowRationale }
    }
    
    /**
     * Extension function to request permissions from an Activity
     */
    fun FragmentActivity.requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        onPermissionsResult: (Map<String, Boolean>) -> Unit
    ) {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsResult ->
            onPermissionsResult(permissionsResult)
        }
        launcher.launch(permissions)
    }
    
    /**
     * Extension function to request permissions from a Fragment
     */
    fun Fragment.requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        onPermissionsResult: (Map<String, Boolean>) -> Unit
    ) {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsResult ->
            onPermissionsResult(permissionsResult)
        }
        launcher.launch(permissions)
    }
}
