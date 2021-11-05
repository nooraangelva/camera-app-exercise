package com.example.cameraapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.core.app.ActivityCompat
import java.util.*
import kotlin.Comparator

class CompareByArea : Comparator<Size> {
    override fun compare(o1: Size?, o2: Size?): Int {
        return o1!!.width*o1.height - o2!!.width*o2.height
    }
}

class DemoCamera(private val onImageAvailableListener: ImageReader.OnImageAvailableListener,private val cameraHandler: Handler, private val textureView : TextureView? = null) {

    private var cameraDevice : CameraDevice? = null

    private var previewReQuestBuilder : CaptureRequest.Builder ? = null
    private var previewRequest : CaptureRequest? = null
    private lateinit var imageReader : ImageReader

    private lateinit var cameraCaptureSession: CameraCaptureSession

    fun openCamera(context : Context){
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        var camIds : Array<String> = emptyArray()

        camIds = cameraManager.cameraIdList

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        cameraManager.openCamera(camIds[0],stateCallBack, cameraHandler)

        var characteristic = cameraManager.getCameraCharacteristics(camIds[0])

        val map = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        if(map == null) {
            Log.v("DemoCameraApp", "map == null")
        }

        val outPutSizes = map?.getOutputSizes(ImageFormat.JPEG)?.asList()
        outPutSizes?.forEach{
            Log.v("DemoCameraApp", "outPutSizes $it")
        }

        val largestRes = Collections.max(outPutSizes, CompareByArea())
        Log.v("DemoCameraApp", "largestRes ${largestRes.height}, ${largestRes.width}")

        imageReader = ImageReader.newInstance(largestRes.width, largestRes.height, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler)

    }

    fun takePhoto() {

        if (cameraDevice == null || imageReader == null){
            Log.v("DemoCameraApp","(cameraDevice == null || imageReader == null)")
        }
        cameraCaptureSession.stopRepeating()
        cameraCaptureSession.abortCaptures()

        try {

            cameraDevice!!.createCaptureSession(Collections.singletonList(imageReader.surface),
                object : CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession){
                        if (cameraDevice == null){
                            Log.v("DemoCameraApp", "onFigured -> null")
                            return
                        }

                        cameraCaptureSession = session
                        doImageCapture()

                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                    }
                }, null)




        } catch (ex : CameraAccessException){
            Log.v("DemoCameraApp","CameraAccessException")
        }
    }

    private fun doImageCapture(){
        Log.v("DemoCameraApp","doImageCapture")

        try {

            var captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            cameraCaptureSession.capture(captureBuilder.build(),captureCallback, null)
            createPreview()


        }catch (ex : CameraAccessException){
            Log.v("DemoCameraApp","CameraAccessException")
        }
    }
    
    private val stateCallBack = object : CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice) {
            Log.v("DemoCameraApp","onOpened")
            cameraDevice = camera
            createPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.v("DemoCameraApp","onDisconnected")

        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.v("DemoCameraApp","onError")
        }
    }


    private fun createPreview(){

        if (textureView == null || cameraDevice == null){
            return
        }

        val texture = textureView.surfaceTexture
        texture?.setDefaultBufferSize(320, 280)

        val surface = Surface(texture)

        cameraDevice!!.createCaptureSession(
            Collections.singletonList(surface),
            object : CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null){
                        return
                    }

                    cameraCaptureSession = session

                    previewReQuestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewReQuestBuilder!!.addTarget(surface)

                    previewRequest = previewReQuestBuilder!!.build()

                    cameraCaptureSession!!.setRepeatingRequest(previewRequest!!, captureCallback, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {

                }
            }, null
        )
    }
    private val captureCallback = object : CameraCaptureSession.CaptureCallback(){

    }
    fun shutDownCamera(){

        imageReader.close()
        cameraCaptureSession.close()
        cameraDevice?.close()
    }
}