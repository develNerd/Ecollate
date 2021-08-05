package org.flepper.ecollation

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.vision.text.TextRecognizer
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlinx.android.synthetic.main.activity_scan_document.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

typealias LumaListener = (luma: Double) -> Unit


class ScanDocumentActivity : AppCompatActivity() {

    private var successDialog: Dialog? = null

    private var message:String? = null



    private lateinit var cameraInfo: CameraInfo

    private lateinit var cameraControl: CameraControl

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private val REQUEST_CODE_PERMISSIONS = 999
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE)
    var decryptedValue:String ?= null
    var transitionsContainer:ViewGroup ?= null
    var image:ImageView ?= null

    var cameraManager:CameraManager? = null

    var animation1:Animation?= null

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var flashLightStatus = false

    private var isRun = false


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_document)

         image = findViewById<View>(R.id.dotanim) as ImageView
         transitionsContainer =
            findViewById(R.id.transition_group) as ViewGroup


        cameraExecutor = Executors.newSingleThreadExecutor()


        successDialog = Dialog(this)

        //flashLightOn()

        imageButton.setOnClickListener {
            toggleTorch()
            setTorchStateObserver()

        }

        capture_but.setOnClickListener {
            takePhoto()
        }

         animation1 = AnimationUtils.loadAnimation(
            applicationContext,
            R.anim.blink
        )
        image!!.startAnimation(animation1)


        if (allPermissionsGranted()) {
                startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        circularProgressBar.apply {
            // Set Progress
            progress = 65f
            // or with animation
            setProgressWithAnimation(65f, 1000) // =1s

            // Set Progress Max
            progressMax = 200f

            // Set ProgressBar Color
            progressBarColor = Color.WHITE
            // or with gradient
            progressBarColorStart = Color.WHITE
            progressBarColorEnd = Color.WHITE
            progressBarColorDirection = CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Set background ProgressBar Color
            backgroundProgressBarColor = Color.WHITE
            // or with gradient
            backgroundProgressBarColorStart = Color.WHITE
            backgroundProgressBarColorEnd = Color.WHITE
            backgroundProgressBarColorDirection = CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Set Width
            progressBarWidth = 7f // in DP
            backgroundProgressBarWidth = 3f // in DP

            // Other
            roundBorder = true
            startAngle = 180f
            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val context = applicationContext

            // Create the TextRecognizer

            // Create the TextRecognizer
            val textRecognizer: TextRecognizer = TextRecognizer.Builder(context).build()
            // TODO: Set the TextRecognizer's Processor.

            // Check if the TextRecognizer is operational.
            // TODO: Set the TextRecognizer's Processor.

            // Check if the TextRecognizer is operational.
            if (!textRecognizer.isOperational) {


                // Check for low storage.  If there is low storage, the native library will not be
                // downloaded, so detection will not become operational.
                val lowstorageFilter = IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)
                val hasLowStorage = registerReceiver(null, lowstorageFilter) != null
                if (hasLowStorage) {
                    Toast.makeText(this, "Low Storage", Toast.LENGTH_LONG).show()
                }
            }

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val qrCodeAnalyzer = QrCodeAnalyzer { qrCodes ->

                Log.d("MainActivity", "QR Code detected: ${qrCodes.text}.")
                decryptedValue = qrCodes.text
                var gottenValue:String? = null
                gottenValue = decryptedValue.toString()

                if (!isRun) {
                    isRun = true
                    if (gottenValue != " ") {
                        // CameraX.unbind(preview)

                        runOnUiThread {
                            boundingBox.visibility = View.GONE
                            image!!.visibility = View.GONE


                            Handler(Looper.getMainLooper()).postDelayed({
                                TransitionManager.beginDelayedTransition(transitionsContainer)
                                circularProgressBar.visibility = View.VISIBLE
                                analysing.visibility = View.VISIBLE
                            }, 1000)


                            Handler(Looper.getMainLooper()).postDelayed({
                                TransitionManager.beginDelayedTransition(transitionsContainer)
                                analysing.text = "Decrypting QRCode..."

                            }, 3000)

                            Handler(Looper.getMainLooper()).postDelayed({
                                val secretKey: String = "662ede816988e58fb6d057d9d85605e0"
                                 message = decryptWithAES(secretKey, gottenValue)
                                if (message != null) {
                                    if (message!!.contains("{")) {
                                        TransitionManager.beginDelayedTransition(transitionsContainer)
                                        circularProgressBar.visibility = View.GONE
                                        analysing.visibility = View.GONE
                                        capture_but.visibility = View.VISIBLE
                                        txtscanDocument.visibility = View.GONE
                                        /*Intent(this, QrcodeDecryptedActivity::class.java).also {
                                            it.putExtra("info",message)
                                            startActivity(it)
                                        }*/
                                    }else{
                                        showSuccesDialog()
                                    }
                                }else{
                                    showSuccesDialog()
                                }


                            }, 5000)


                        }


                    }
                }else{
                    return@QrCodeAnalyzer
                }

            }


            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, qrCodeAnalyzer)
                }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()


            // Preview
            preview = Preview.Builder()
                .build()

            // Select back camera



            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()


               val  camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)


                // Bind use cases to camera
                preview?.setSurfaceProvider(viewFinder.createSurfaceProvider(camera.cameraInfo))



                cameraInfo = camera.cameraInfo

                cameraControl = camera.cameraControl

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }



        }, ContextCompat.getMainExecutor(this))

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return


        // Create timestamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")


        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()



        // Setup image capture listener which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }


                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Intent(this@ScanDocumentActivity, QrcodeDecryptedActivity::class.java).also {
                        it.putExtra("info",message)
                        it.putExtra("uri",savedUri)
                        it.putExtra("FILE_PATH",photoFile.absolutePath)
                        startActivity(it)
                    }
                    Log.d(TAG, msg)
                }
            })
    }


    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }








    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                    //startCameraForPreview()
                    startCamera()
            } else {
                "Permissions not granted by the user.".toast()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun String.toast() {
        Toast.makeText(
            this@ScanDocumentActivity,
            this,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setTorchStateObserver() {
        cameraInfo.torchState.observe(this, androidx.lifecycle.Observer { state ->
            if (state == TorchState.ON) {
                imageButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_flash_on_24dp
                    )
                )
            } else {
                imageButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_flash_off_24dp
                    )
                )
            }
        })
    }

    private fun toggleTorch() {
        if (cameraInfo?.torchState?.value == TorchState.ON) {
            cameraControl?.enableTorch(false)
        } else {
            cameraControl?.enableTorch(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun flashLightOn() {
        val cameraManager: CameraManager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {

            val cameraId: String = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, true)
            flashLightStatus = true
        } catch (e: CameraAccessException) {
            toast(e.message.toString())
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private fun showSuccesDialog(){
        val li: LayoutInflater = LayoutInflater.from(this)
        val view: View = li.inflate(R.layout.custompopup, null)
        val btnOkay: Button = view.findViewById(R.id.okay) as Button
        btnOkay.setOnClickListener {
           startActivity(intent)
        }
        successDialog?.setContentView(view)
        successDialog?.setCanceledOnTouchOutside(false)
        successDialog?.setOnCancelListener {
            successDialog?.dismiss()
        }
        successDialog?.show()

    }


    fun decryptWithAES(key: String, strToDecrypt: String?): String? {
        Security.addProvider(BouncyCastleProvider())
        var keyBytes: ByteArray

        try {
            keyBytes = key.toByteArray(charset("UTF8"))
            val skey = SecretKeySpec(keyBytes, "AES")
            val input = org.bouncycastle.util.encoders.Base64
                .decode(strToDecrypt?.trim { it <= ' ' }?.toByteArray(charset("UTF8")))

            synchronized(Cipher::class.java) {
                val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding")
                cipher.init(Cipher.DECRYPT_MODE, skey)

                val plainText = ByteArray(cipher.getOutputSize(input.size))
                var ptLength = cipher.update(input, 0, input.size, plainText, 0)
                ptLength += cipher.doFinal(plainText, ptLength)
                val decryptedString = String(plainText)
                return decryptedString.trim { it <= ' ' }
            }
        } catch (uee: UnsupportedEncodingException) {
            uee.printStackTrace()
            showSuccesDialog()
        } catch (ibse: IllegalBlockSizeException) {
            ibse.printStackTrace()
            showSuccesDialog()

        } catch (bpe: BadPaddingException) {
            showSuccesDialog()
            bpe.printStackTrace()
        } catch (ike: InvalidKeyException) {
            showSuccesDialog()
            ike.printStackTrace()
        } catch (nspe: NoSuchPaddingException) {
            showSuccesDialog()
            nspe.printStackTrace()
        } catch (nsae: NoSuchAlgorithmException) {
            showSuccesDialog()
            nsae.printStackTrace()
        } catch (e: ShortBufferException) {
            showSuccesDialog()
            e.printStackTrace()
        }

        return null
    }


}

private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {

    private val frameRateWindow = 8
    private val frameTimestamps = ArrayDeque<Long>(5)
    private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
    private var lastAnalyzedTimestamp = 0L
    var framesPerSecond: Double = -1.0
        private set

    /**
     * Used to add listeners that will be called with each luma computed
     */
    fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

    /**
     * Helper extension function used to extract a byte array from an image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    /**
     * Analyzes an image to produce a result.
     *
     * <p>The caller is responsible for ensuring this analysis method can be executed quickly
     * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
     * images will not be acquired and analyzed.
     *
     * <p>The image passed to this method becomes invalid after this method returns. The caller
     * should not store external references to this image, as these references will become
     * invalid.
     *
     * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
     * call image.close() on received images when finished using them. Otherwise, new images
     * may not be received or the camera may stall, depending on back pressure setting.
     *
     */
    override fun analyze(image: ImageProxy) {
        // If there are no listeners attached, we don't need to perform analysis
        if (listeners.isEmpty()) {
            image.close()
            return
        }

        // Keep track of frames analyzed
        val currentTime = System.currentTimeMillis()
        frameTimestamps.push(currentTime)

        // Compute the FPS using a moving average
        while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
        val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
        val timestampLast = frameTimestamps.peekLast() ?: currentTime
        framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

        // Analysis could take an arbitrarily long amount of time
        // Since we are running in a different thread, it won't stall other use cases

        lastAnalyzedTimestamp = frameTimestamps.first

        // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
        val buffer = image.planes[0].buffer

        // Extract image data from callback object
        val data = buffer.toByteArray()

        // Convert the data into an array of pixel values ranging 0-255
        val pixels = data.map { it.toInt() and 0xFF }

        // Compute average luminance for the image
        val luma = pixels.average()

        // Call all listeners with new value
        listeners.forEach { it(luma) }

        image.close()
    }







}



