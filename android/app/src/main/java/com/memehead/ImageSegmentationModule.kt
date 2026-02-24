package com.memehead

import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod


class ImageSegmentationModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {


    override fun getName() = "ImageSegmentationModule"

    @ReactMethod
    fun segmentImage(uriString: String?, destUriString: String?, promise: Promise) {
        Thread(Runnable {
            SegmentationTask().executeTask(uriString, destUriString, promise, reactContext)
            Log.d("segmentImage: ", "done");
//            promise.resolve(null)
        }).start()
    }

}