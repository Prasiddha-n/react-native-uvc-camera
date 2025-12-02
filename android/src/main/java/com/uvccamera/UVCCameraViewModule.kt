package com.uvccamera

import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.UIManagerHelper
import com.uvccamera.utils.withPromise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UVCCameraViewModule(reactContext: ReactApplicationContext?) :
  ReactContextBaseJavaModule(reactContext) {
  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  override fun getName() = TAG

  private fun resolveCameraView(viewId: Int): UVCCameraView? {
    val context = reactApplicationContext ?: return null
    val uiManager = UIManagerHelper.getUIManager(context, viewId)
    return try {
      uiManager?.resolveView(viewId) as? UVCCameraView
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to resolve view $viewId", t)
      null
    }
  }

  private fun findCameraViewOrNull(viewId: Int): UVCCameraView? {
    val view = resolveCameraView(viewId)
    if (view == null) {
      Log.w(TAG, "Command skipped: view $viewId is not available.")
    }
    return view
  }

  private fun requireCameraView(viewId: Int): UVCCameraView {
    return resolveCameraView(viewId) ?: throw ViewNotFoundError(viewId)
  }

  @ReactMethod
  fun openCamera(viewTag: Int) {
    findCameraViewOrNull(viewTag)?.openCamera()
  }

  @ReactMethod
  fun closeCamera(viewTag: Int) {
    findCameraViewOrNull(viewTag)?.closeCamera()
  }

  @ReactMethod
  fun updateAspectRatio(viewTag: Int, width: Int, height: Int) {
    findCameraViewOrNull(viewTag)?.updateAspectRatio(width, height)
  }

  @ReactMethod
  fun setCameraBright(viewTag: Int, brightness: Int) {
    findCameraViewOrNull(viewTag)?.setCameraBright(brightness)
  }

  @ReactMethod
  fun setContrast(viewTag: Int, contrast: Int) {
    findCameraViewOrNull(viewTag)?.setContrast(contrast)
  }

  /**
   * Temporary alias to avoid breaking older JS that calls the misspelled method.
   */
  @ReactMethod
  fun setContast(viewTag: Int, contrast: Int) {
    setContrast(viewTag, contrast)
  }

  @ReactMethod
  fun setSaturation(viewTag: Int, saturation: Int) {
    findCameraViewOrNull(viewTag)?.setSaturation(saturation)
  }

  @ReactMethod
  fun setSharpness(viewTag: Int, sharpness: Int) {
    findCameraViewOrNull(viewTag)?.setSharpness(sharpness)
  }

  @ReactMethod
  fun setZoom(viewTag: Int, zoom: Int) {
    findCameraViewOrNull(viewTag)?.setZoom(zoom)
  }


  @ReactMethod
  fun takePhoto(viewTag: Int, promise: Promise) {
    coroutineScope.launch {
      withPromise(promise) {
        val view = requireCameraView(viewTag)
        view.takePhoto()
      }
    }
  }

  @ReactMethod
  fun setDefaultCameraVendorId(viewTag: Int, vendorId: Int) {
    findCameraViewOrNull(viewTag)?.setDefaultCameraVendorId(vendorId)
  }
}
