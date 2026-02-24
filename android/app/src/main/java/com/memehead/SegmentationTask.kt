package com.memehead

import android.R
import android.graphics.Bitmap
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class SegmentationTask: ImageSegmenterHelper.SegmenterListener {
    private var imageSegmenterHelper: ImageSegmenterHelper? = null
    private var backgroundScope: CoroutineScope? = null

    fun resize(source: Bitmap): Bitmap {
        var scale = 0.0F
        var newSize = 0

        scale = INPUT_IMAGE_MAX_WIDTH as Float / source.width
        newSize = (source.height * scale).roundToInt().toInt()
        var scaledBitmap: Bitmap =
            Bitmap.createScaledBitmap(source, INPUT_IMAGE_MAX_WIDTH.toInt(), newSize, true)

        if (scaledBitmap !== source) {
            source.recycle()
        }

        return scaledBitmap
    }

    fun executeTask(uriString: String?, destUriString: String?, promise: Promise, reactContext: ReactApplicationContext) {
        // Implement your CPU-intensive AI computation here
        // This is just a placeholder method; replace it with your actual AI processing code.
        Log.d("ImageSegmentationModule", "segmentImage called with uriString: $uriString and destUriString: $destUriString")

//        Log.d("ImageSegmentationModule", GLES31.glGetString(GLES31.GL_VERSION).toString())
        if (imageSegmenterHelper == null) {
            backgroundScope = CoroutineScope(Dispatchers.IO)
            imageSegmenterHelper = ImageSegmenterHelper(context= reactContext, imageSegmenterListener = this)
        }

        // Run image segmentation on the input image
        var inputImage = bitmapFromUriString(uriString, promise, reactContext) ?: return
        inputImage = resize(inputImage)

        Log.d("ImageSegmentationModule", "resized: ${inputImage.width} : ${inputImage.height}")

        backgroundScope?.launch {
            val mpImage = BitmapImageBuilder(inputImage).build()
            val result = imageSegmenterHelper?.segmentImageFile(mpImage)
            Log.d("ImageSegmentationModule", "result: ${result.toString()}")

            val newImage = result?.categoryMask()?.get()!!
            val resultBundle = ImageSegmenterHelper.ResultBundle(
                ByteBufferExtractor.extract(newImage),
                newImage.width,
                newImage.height,
                result.timestampMs()
            )

            Log.d("ImageSegmentationModule", "result: ${resultBundle.toString()}")

            runBlocking {
                withContext(Dispatchers.Main) {
                    Log.d("ImageSegmentationModule", String.format("%d ms", resultBundle.inferenceTime))

                    val bitmap = Bitmap.createBitmap(R.attr.width, R.attr.height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(resultBundle.results)

                    val file = File(destUriString)
                    writeBMPToPNGFile(bitmap, file, promise)
                    val map = buildImageReactMap(file, bitmap)
                    promise.resolve(map)
                    Log.d("ImageSegmentationModule", "All done!")
                }
            }


        }

        imageSegmenterHelper?.clearImageSegmenter()
        imageSegmenterHelper?.clearListener()

        imageSegmenterHelper = null
        backgroundScope = null

//        val inputImage = bitmapFromUriString(uriString, promise, reactContext) ?: return
//        val mpImage = BitmapImageBuilder(inputImage).build()
//        val result = imageSegmenterHelper?.segmentImageFile(mpImage)

//        Log.d("ImageSegmentationModule", "${result.toString()}")
//
//        val mask = result?.categoryMask()?.get()
//        val buffer = ByteBufferExtractor.extract(mask)
//        buffer.rewind()
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        bitmap.copyPixelsFromBuffer(buffer)
//
//        val file = File(destUriString)
//        writeBMPToPNGFile(bitmap, file, promise)
//        val map = buildImageReactMap(file, bitmap)
//        promise.resolve(map)
    }

    override fun onError(error: String, errorCode: Int) {
        TODO("Not yet implemented")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        TODO("Not yet implemented")
    }

    companion object {
        private const val INPUT_IMAGE_MAX_WIDTH = 512F
    }
}