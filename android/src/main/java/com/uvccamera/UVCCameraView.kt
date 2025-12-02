package com.uvccamera

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.SurfaceHolder
import android.widget.FrameLayout
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactContext
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Size
import com.serenegiant.widget.AspectRatioSurfaceView
import kotlin.math.abs

const val TAG = "UVCCameraView"

class UVCCameraView(context: Context) : FrameLayout(context), LifecycleEventListener {

  companion object {
    private const val DEBUG = true
    private const val PREF_CAMERA = "camera"
    private const val PREF_WIDTH = "width"
    private const val PREF_HEIGHT = "height"
    private const val PREF_VENDOR_ID = "defaultCameraVendorId"
    private const val DEFAULT_WIDTH = 2592
    private const val DEFAULT_HEIGHT = 1944
    private const val DEFAULT_ROTATION = 180
    private const val DEFAULT_ZOOM = 500
    private const val DEFAULT_VENDOR_ID = 3034
    private const val DEFAULT_FPS = 25
  }

  private val reactContext: ReactContext
    get() = context as ReactContext

  var mCameraHelper: ICameraHelper? = null
    private set
  private val mCameraViewMain: AspectRatioSurfaceView =
    AspectRatioSurfaceView(reactContext).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

  private val cameraPrefs by lazy {
    reactContext.getSharedPreferences(PREF_CAMERA, Context.MODE_PRIVATE)
  }

  private var hasSurface = false
  private var shouldResumeCamera = false
  private var lifecycleRegistered = false

  private val surfaceCallback = object : SurfaceHolder.Callback {
    override fun surfaceCreated(holder: SurfaceHolder) {
      if (DEBUG) Log.d(TAG, "surfaceCreated() holder=$holder")
      hasSurface = true
      ensureCameraHelper()
      mCameraHelper?.addSurface(holder.surface, false)
      if (shouldResumeCamera) {
        selectPreferredDevice()
      }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
      if (DEBUG) Log.d(TAG, "surfaceDestroyed() holder=$holder")
      hasSurface = false
      mCameraHelper?.removeSurface(holder.surface)
      closeCameraInternal(keepResumeFlag = true)
    }
  }

  private val mStateListener: ICameraHelper.StateCallback = object : ICameraHelper.StateCallback {
    override fun onAttach(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onAttach:${device.deviceName}")
      if (shouldResumeCamera) {
        post { selectPreferredDevice() }
      }
    }

    override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
      if (DEBUG) Log.v(TAG, "onDeviceOpen:$isFirstOpen")
      mCameraHelper?.openCamera()
    }

    override fun onCameraOpen(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onCameraOpen:")
      mCameraHelper?.run {
        configurePreviewSize(this)
        try {
          previewConfig = previewConfig?.setRotation(DEFAULT_ROTATION % 360)
          previewConfig = previewConfig?.setMirror(MirrorMode.MIRROR_HORIZONTAL)
        } catch (t: Throwable) {
          Log.w(TAG, "Unable to update preview config", t)
        }
        try {
          val control = uvcControl
          control.zoomRelative = DEFAULT_ZOOM
        } catch (t: Throwable) {
          Log.w(TAG, "Unable to apply default zoom", t)
        }
        startPreview()
        if (hasSurface) {
          addSurface(mCameraViewMain.holder.surface, false)
        }
      }
    }

    override fun onCameraClose(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onCameraClose:")
      mCameraHelper?.removeSurface(mCameraViewMain.holder.surface)
    }

    override fun onDeviceClose(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onDeviceClose:")
    }

    override fun onDetach(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onDetach:")
    }

    override fun onCancel(device: UsbDevice) {
      if (DEBUG) Log.v(TAG, "onCancel:")
    }
  }

  init {
    registerLifecycleListener()
    mCameraViewMain.holder.addCallback(surfaceCallback)
    addView(mCameraViewMain)
  }

  private fun registerLifecycleListener() {
    if (!lifecycleRegistered) {
      reactContext.addLifecycleEventListener(this)
      lifecycleRegistered = true
    }
  }

  private fun unregisterLifecycleListener() {
    if (lifecycleRegistered) {
      reactContext.removeLifecycleEventListener(this)
      lifecycleRegistered = false
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    ensureCameraHelper()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cleanup()
  }

  override fun onHostResume() {
    if (shouldResumeCamera && hasSurface) {
      post { selectPreferredDevice() }
    }
  }

  override fun onHostPause() {
    closeCameraInternal(keepResumeFlag = true)
  }

  override fun onHostDestroy() {
    cleanup()
  }

  private fun ensureCameraHelper(): ICameraHelper? {
    if (mCameraHelper == null) {
      mCameraHelper = CameraHelper().apply {
        setStateCallback(mStateListener)
      }
    }
    return mCameraHelper
  }

  private fun selectPreferredDevice(): Boolean {
    val helper = mCameraHelper ?: return false
    val devices = helper.deviceList
    if (devices == null || devices.isEmpty()) {
      Log.w(TAG, "No UVC devices detected.")
      return false
    }

    val preferredVendorId = cameraPrefs.getInt(PREF_VENDOR_ID, DEFAULT_VENDOR_ID)
    val selectedDevice = devices.firstOrNull { preferredVendorId > 0 && it.vendorId == preferredVendorId }
      ?: devices.first()
    if (DEBUG) Log.d(TAG, "Selecting device: ${selectedDevice.deviceName}")
    helper.selectDevice(selectedDevice)
    return true
  }

  private fun configurePreviewSize(helper: ICameraHelper) {
    val supportedSizes = helper.supportedSizeList
    if (supportedSizes == null || supportedSizes.isEmpty()) {
      Log.w(TAG, "No supported preview sizes returned by helper.")
      return
    }

    val preferredWidth = cameraPrefs.getInt(PREF_WIDTH, DEFAULT_WIDTH)
    val preferredHeight = cameraPrefs.getInt(PREF_HEIGHT, DEFAULT_HEIGHT)

    val selectedSize =
      supportedSizes.minByOrNull { size ->
        val widthDiff = abs(size.width - preferredWidth)
        val heightDiff = abs(size.height - preferredHeight)
        widthDiff + heightDiff
      } ?: supportedSizes.first()

    selectedSize.fps = DEFAULT_FPS
    helper.previewSize = selectedSize
    mCameraViewMain.setAspectRatio(selectedSize.width, selectedSize.height)
  }

  private fun closeCameraInternal(keepResumeFlag: Boolean) {
    mCameraHelper?.closeCamera()
    if (!keepResumeFlag) {
      shouldResumeCamera = false
    }
  }

  fun openCamera() {
    shouldResumeCamera = true
    ensureCameraHelper()
    if (!selectPreferredDevice()) {
      Log.w(TAG, "openCamera called but no camera was available.")
    }
  }

  fun closeCamera() {
    closeCameraInternal(keepResumeFlag = false)
  }

  fun updateAspectRatio(width: Int, height: Int) {
    cameraPrefs.edit()
      .putInt(PREF_WIDTH, width)
      .putInt(PREF_HEIGHT, height)
      .apply()

    if (shouldResumeCamera) {
      closeCameraInternal(keepResumeFlag = true)
      post { selectPreferredDevice() }
    }
  }

  fun setCameraBright(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.brightnessPercent = value
    }.onFailure { Log.w(TAG, "Failed to set brightness", it) }
  }

  fun setDefaultCameraVendorId(value: Int) {
    cameraPrefs.edit().putInt(PREF_VENDOR_ID, value).apply()
    if (shouldResumeCamera) {
      closeCameraInternal(keepResumeFlag = true)
      post { selectPreferredDevice() }
    }
  }

  fun setContrast(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.contrast = value
    }.onFailure { Log.w(TAG, "Failed to set contrast", it) }
  }

  fun setHue(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.hue = value
    }.onFailure { Log.w(TAG, "Failed to set hue", it) }
  }

  fun setSaturation(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.saturation = value
    }.onFailure { Log.w(TAG, "Failed to set saturation", it) }
  }

  fun setSharpness(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.sharpness = value
    }.onFailure { Log.w(TAG, "Failed to set sharpness", it) }
  }

  fun setZoom(value: Int) {
    runCatching {
      mCameraHelper?.uvcControl?.apply {
        zoomRelative = value
        focusAuto = true
      }
    }.onFailure { Log.w(TAG, "Failed to set zoom", it) }
  }

  fun rotateCamera() {
    runCatching {
      mCameraHelper?.previewConfig = mCameraHelper?.previewConfig?.setMirror(MirrorMode.MIRROR_HORIZONTAL)
    }.onFailure { Log.w(TAG, "Failed to rotate camera", it) }
  }

  fun reset() {
    runCatching {
      mCameraHelper?.uvcControl?.apply {
        resetBrightness()
        resetContrast()
        resetHue()
        resetSaturation()
        resetSharpness()
      }
    }.onFailure { Log.w(TAG, "Failed to reset camera controls", it) }
  }

  fun cleanup() {
    if (DEBUG) Log.d(TAG, "cleanup()")
    shouldResumeCamera = false
    hasSurface = false
    mCameraHelper?.removeSurface(mCameraViewMain.holder.surface)
    mCameraHelper?.closeCamera()
    runCatching {
      mCameraHelper?.release()
    }.onFailure { Log.w(TAG, "Failed to release camera helper", it) }
    mCameraHelper = null
    unregisterLifecycleListener()
  }

  private fun selectDevice(device: UsbDevice) {
    if (DEBUG) Log.v(TAG, "selectDevice:${device.deviceName}")
    mCameraHelper?.selectDevice(device)
  }
}
