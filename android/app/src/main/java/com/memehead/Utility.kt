package com.memehead

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color;
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID


private val SCHEME_FILE = "file"
private val SCHEME_CONTENT = "content"
val EOF = -1
private val DEFAULT_BUFFER_SIZE = 1024 * 4

fun convertStreamToString(`is`: InputStream?, promise: Promise): String? {
    try {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        var line: String? = null
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString()
    } catch (e: IOException) {
        handleError(e, promise)
    }
    return null
}

fun getStringFromFile(filePath: String?, promise: Promise): String? {
    try {
        val fl = File(filePath)
        val fin = FileInputStream(fl)
        val ret = convertStreamToString(fin, promise)
        //Make sure you close all streams.
        fin.close()
        return ret
    } catch (e: IOException) {
        handleError(e, promise)
    }
    return null
}

fun buildImageReactMap(file: File, bmp: Bitmap): WritableMap? {
    val map = Arguments.createMap()
    map.putString("uri", "file://$file")
    map.putDouble("width", bmp.width.toDouble())
    map.putDouble("height", bmp.height.toDouble())
    return map
}

@Throws(IOException::class)
fun copyLarge(input: InputStream, output: OutputStream, buffer: ByteArray?): Long {
    var count: Long = 0
    var n: Int
    while (EOF != input.read(buffer).also { n = it }) {
        output.write(buffer, 0, n)
        count += n.toLong()
    }
    return count
}

@Throws(IOException::class)
fun copy(input: InputStream, output: OutputStream): Int {
    val count = copyLarge(input, output, ByteArray(DEFAULT_BUFFER_SIZE))
    return if (count > Int.MAX_VALUE) {
        -1
    } else count.toInt()
}

fun bitmapFromUriString(uriString: String?, promise: Promise, context: Context): Bitmap? {
    try {
        val uri = Uri.parse(uriString)
        val scheme = Uri.parse(uriString).scheme
        if (scheme != null && (scheme == SCHEME_CONTENT || scheme == SCHEME_FILE)) {
            val resolver = context.contentResolver
            try {
                val streamOrient = resolver.openInputStream(uri)
                val orientation: Int = getOrientation(streamOrient)
                val streamBmp = resolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(streamBmp)
                return fixOrientation(bmp, orientation)
            } catch (e: FileNotFoundException) {
                handleError(e, promise)
            }
        } else {
            val id = ResourceDrawableIdHelper.getInstance().getResourceDrawableId(context, uriString)
            if (id > 0) {
                return BitmapFactory.decodeResource(context.resources, id)
            }
        }
        val url = URL(uriString)
        try {
            val connection = url.openConnection()
            val inputStream = connection.getInputStream()
            val tempFile = File(context.filesDir, UUID.randomUUID().toString() + ".tmp")
            val outputStream = FileOutputStream(tempFile)
            copy(inputStream, outputStream)
            val orientation: Int = getOrientation(tempFile.absolutePath)
            val bmp = BitmapFactory.decodeFile(tempFile.absolutePath)
            tempFile.delete()
            return fixOrientation(bmp, orientation)
        } catch (e: IOException) {
            handleError(e, promise)
        }
    } catch (e: MalformedURLException) {
        handleError(e, promise)
    }
    return null
}

fun handleError(e: Exception, promise: Promise) {
    e.printStackTrace(System.err)
    promise.reject(e)
}

fun createRandomPNGFile(context: ReactContext): File? {
    val filename = UUID.randomUUID().toString() + ".png"
    return File(context.filesDir, filename)
}


fun writeBMPToPNGFile(bmp: Bitmap, file: File?, promise: Promise) {
    try {
        val out = FileOutputStream(file)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
    } catch (e: IOException) {
        handleError(e, promise)
    }
}

fun trimTransparency(source: Bitmap): Bitmap? {
    var firstX = 0
    var firstY = 0
    var lastX = source.width
    var lastY = source.height
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    loop@ for (x in 0 until source.width) {
        for (y in 0 until source.height) {
            if (pixels[x + y * source.width] != Color.TRANSPARENT) {
                firstX = x
                break@loop
            }
        }
    }
    loop@ for (y in 0 until source.height) {
        for (x in firstX until source.width) {
            if (pixels[x + y * source.width] != Color.TRANSPARENT) {
                firstY = y
                break@loop
            }
        }
    }
    loop@ for (x in source.width - 1 downTo firstX) {
        for (y in source.height - 1 downTo firstY) {
            if (pixels[x + y * source.width] != Color.TRANSPARENT) {
                lastX = x
                break@loop
            }
        }
    }
    loop@ for (y in source.height - 1 downTo firstY) {
        for (x in source.width - 1 downTo firstX) {
            if (pixels[x + y * source.width] != Color.TRANSPARENT) {
                lastY = y
                break@loop
            }
        }
    }
    return Bitmap.createBitmap(source, firstX, firstY, lastX - firstX, lastY - firstY)
}

fun calcRectForContainedRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): HashMap<*, *>? {
    val width: Int
    val height: Int
    val x: Int
    val y: Int
    if (srcWidth > srcHeight) {
        width = dstWidth
        height = srcHeight * dstWidth / srcWidth
        x = 0
        y = dstHeight / 2 - height / 2
    } else {
        width = srcWidth * dstHeight / srcHeight
        height = dstHeight
        x = dstWidth / 2 - width / 2
        y = 0
    }
    val map: HashMap<String, Int> = HashMap<String, Int>()
    map["width"] = width
    map["height"] = height
    map["x"] = x
    map["y"] = y
    return map
}

fun getOrientation(filename: String?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            ExifInterface(filename!!).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (e: IOException) {
            e.printStackTrace(System.err)
            -1
        }
    } else {
        -1
    }
}


fun getOrientation(stream: InputStream?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            ExifInterface(stream!!).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (e: IOException) {
            e.printStackTrace(System.err)
            -1
        }
    } else {
        -1
    }
}

fun fixOrientation(bitmap: Bitmap, orientation: Int): Bitmap? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> return bitmap
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return try {
        val bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        bmRotated
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        null
    }
}
