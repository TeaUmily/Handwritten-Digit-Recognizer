package com.example.mydigitrecognizer

import com.example.mydigitrecognizer.model.ResponseResult
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.bumptech.glide.Glide
import com.example.mydigitrecognizer.api.RepositoryRetriever
import com.example.mydigitrecognizer.model.ClassificationRequestData
import com.example.mydigitrecognizer.model.Inputs
import com.example.mydigitrecognizer.utils.rotateBitmap
import com.example.mydigitrecognizer.utils.toSquare
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestCameraPermission()

        btnTakePicture.setOnClickListener {
            if (buttonState == ButtonState.ANALYZE_PHOTO && currentPhotoFile != null) {
                currentPhotoFile?.apply {
                    makeNetworkRequest(getTransformedBitmapData(this))
                }
            } else {
                if (currentPhotoFile == null) {
                    takePhoto()
                    buttonState = ButtonState.ANALYZE_PHOTO
                }
            }
        }

        btnRepeat.setOnClickListener {
            buttonState = ButtonState.TAKE_PHOTO
            btnTakePicture.background =
                ContextCompat.getDrawable(baseContext, R.drawable.ic_take_photo)
            currentPhotoFile?.delete()
            currentPhotoFile = null
            imageView.visibility = View.GONE
        }

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun getTransformedBitmapData(photoFile: File): List<Int> {
        val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        val rotatedBitmap = rotateBitmap(originalBitmap)
        val squaredBitmap = rotatedBitmap.toSquare()

        /** Scale bitmap to fit 32x32 array requirements */
        val scaledBitmap = Bitmap.createScaledBitmap(squaredBitmap, 32, 32, true)
        scaledBitmap.density = 32
        /** Transform bitmap color values to 0 or 1 depending on the grayscale
         * and save them to newly created array */
        val bitmapValuesArray = Array(32) { IntArray(32) }
        for (i in 0 until scaledBitmap.width) {
            for (x in 0 until scaledBitmap.height) {
                val color = scaledBitmap.getColor(i, x)
                val grayScale = if ((color.red() + color.green() + color.blue()) / 3 <= 0.5) {
                    0
                } else {
                    1
                }
                bitmapValuesArray[i][x] = grayScale
            }
        }

        /** Shrink values array to fit 8x8 array requirements and save it to list */
        val compressedValuesArray = mutableListOf<Int>()
        for (i in 0 until 32 step 4) {
            for (j in 0 until 32 step 4) {
                compressedValuesArray.add(getRepresentativeNumber(j, i, bitmapValuesArray))
            }
        }

        /** Return transformed file */
        return compressedValuesArray
    }

    private fun makeNetworkRequest(values: List<Int>) {
        val map = mutableMapOf<String, Int>()
        values.forEachIndexed { index, value ->
            map["Col${index + 1}"] = value
        }
        map["Col65"] = 0
        val requestData = ClassificationRequestData(Inputs(listOf(map)))

        if (isNetworkConnected()) {
            repoRetriever.getClassificationResult(requestData, provideNetworkCallback())
        } else {
            AlertDialog.Builder(this).setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and try again")
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .setIcon(android.R.drawable.ic_dialog_alert).show()
        }
    }

    private fun provideNetworkCallback() = object : Callback<ResponseResult> {
        override fun onFailure(call: Call<ResponseResult>?, t: Throwable?) {
            Log.e("MainActivity", "Problem calling Azure API {${t?.message}}")
        }

        override fun onResponse(call: Call<ResponseResult>?, response: Response<ResponseResult>?) {
            response?.isSuccessful.let {
                val resultList = response?.body()?.results?.output ?: emptyList()
                Toast.makeText(
                    this@MainActivity,
                    "Recognized number: ${resultList[0].recognizedNumber}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities != null &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getRepresentativeNumber(
        startIndexRow: Int,
        startIndexColumn: Int,
        bitmap: Array<IntArray>
    ): Int {
        var sum = 0
        for (i in startIndexRow until startIndexRow + 4) {
            for (j in startIndexColumn until startIndexColumn + 4) {
                val p = bitmap[i][j]
                if (p == 0) {
                    sum++
                }
            }
        }
        return sum
    }

    private fun requestCameraPermission() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
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
                    btnTakePicture.background =
                        ContextCompat.getDrawable(baseContext, R.drawable.ic_done)
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
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
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
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
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