package com.example.mydigitrecognizer

import com.example.mydigitrecognizer.model.ResponseResult
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.AspectRatio.RATIO_4_3
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.example.mydigitrecognizer.api.RepositoryRetriever
import com.example.mydigitrecognizer.model.ClassificationRequestData
import com.example.mydigitrecognizer.model.Inputs
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var currentPhotoFile: File? = null
    private var buttonState = ButtonState.TAKE_PHOTO
    private val repoRetriever = RepositoryRetriever()

    private val callback = object : Callback<ResponseResult> {
        override fun onFailure(call: Call<ResponseResult>?, t:Throwable?) {
            Log.e("MainActivity", "Problem calling Github API {${t?.message}}")
        }

        override fun onResponse(call: Call<ResponseResult>?, response: Response<ResponseResult>?) {
            response?.isSuccessful.let {
                val resultList = response?.body()?.results?.output ?: emptyList()
                Toast.makeText(this@MainActivity, "Recognized number: ${resultList[0].recognizedNumber}", Toast.LENGTH_SHORT).show()
            }
        }
    }


        @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestCameraPermission()

        btnTakePicture.setOnClickListener {
            if(buttonState == ButtonState.ANALYZE_PHOTO && currentPhotoFile != null) {

                val originalBitmap = BitmapFactory.decodeFile(currentPhotoFile!!.absolutePath)

                val matrix = Matrix()

                matrix.postRotate(90f)

                val bmOriginal = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )

                Glide.with(baseContext)
                    .load(bmOriginal)
                    .into(imageView)
                val squareB = bmOriginal.toSquare()
                if(squareB != null) {
                    val b = Bitmap.createScaledBitmap(squareB, 32, 32, true)
                    b.density = 32

                    var intArray = IntArray(b.width * b.height)


                    b.getPixels(intArray, 0, b.width, 0, 0, b.width, b.height)

                    val d = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.RGB_565)


                    val array2 = Array(32) { IntArray(32) }
                    for (i in 0 until b.width) {
                        for (x in 0 until b.height) {
                            val oc = b.getColor(i, x)
                            val grayScale = if( (oc.red()+oc.green()+oc.blue()) / 3 <= 0.5) {
                                0f
                            } else {
                                1f
                            }
                            array2[i][x] = grayScale.toInt()
                            d.setPixel(i, x, Color.rgb(grayScale, grayScale, grayScale))
                        }
                    }

                    val array = mutableListOf<Int>()

                    for(i in 0 until 32 step 4){
                        for (j in 0 until 32 step 4){
                            array.add(getInfoNumber(j, i, array2))
                        }
                    }

                    d.getPixels(intArray, 0, d.width, 0, 0, d.width, d.height)

                    Log.e("MOJE POLJE", array.subList(0,8).toString())
                    Log.e("MOJE POLJE", array.subList(8,16).toString())
                    Log.e("MOJE POLJE", array.subList(16,24).toString())
                    Log.e("MOJE POLJE", array.subList(24,32).toString())
                    Log.e("MOJE POLJE", array.subList(32,40).toString())
                    Log.e("MOJE POLJE", array.subList(40,48).toString())
                    Log.e("MOJE POLJE", array.subList(48,56).toString())
                    Log.e("MOJE POLJE", array.subList(56,64).toString())

                    val map = mutableMapOf<String, Int>()
                    array.forEachIndexed { index, value ->
                        map["Col${index+1}"] = value
                    }
                    map["Col65"] = 0
                    val requestData = ClassificationRequestData(Inputs(listOf(map)))

                    if (isNetworkConnected()) {
                        repoRetriever.getClassificationResult(requestData, callback)
                    } else {
                        AlertDialog.Builder(this).setTitle("No Internet Connection")
                            .setMessage("Please check your internet connection and try again")
                            .setPositiveButton(android.R.string.ok) { _, _ -> }
                            .setIcon(android.R.drawable.ic_dialog_alert).show()
                    }
                }

            } else {
                if(currentPhotoFile == null) {
                    takePhoto()
                    buttonState = ButtonState.ANALYZE_PHOTO
                }
            }
        }

        btnRepeat.setOnClickListener {
            buttonState = ButtonState.TAKE_PHOTO
            btnTakePicture.background = ContextCompat.getDrawable(baseContext, R.drawable.ic_take_photo)
            currentPhotoFile?.delete()
            currentPhotoFile = null
            imageView.visibility = View.GONE
        }

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities != null &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getInfoNumber(startIndexRow: Int, startIndexColumn: Int, bitmap: Array<IntArray>) : Int {
        var sum = 0
        for(i in startIndexRow until startIndexRow + 4){
            for (j in startIndexColumn until startIndexColumn + 4){
                val p = bitmap[i][j]
                if(p == 0) {
                    sum++
                }
            }
        }
        return sum
    }

    private fun Bitmap.toSquare(): Bitmap? {
        val side = kotlin.math.min(width, height)
        val xOffset = (width - side)/2
        val yOffset = (height - side)/2

        return Bitmap.createBitmap(
            this,
            xOffset,
            yOffset,
            side,
            side
        )
    }

    private fun requestCameraPermission() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    currentPhotoFile = photoFile
                    imageView.visibility = View.VISIBLE
                    Glide.with(this@MainActivity)
                        .load(savedUri)
                        .into(imageView)
                    btnTakePicture.background = ContextCompat.getDrawable(baseContext, R.drawable.ic_done)
                }
            })
    }

    private fun startCamera() {
        imageView.visibility = View.GONE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(RATIO_4_3)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
                //camera.cameraControl.enableTorch(true)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

enum class ButtonState {
    TAKE_PHOTO, ANALYZE_PHOTO
}