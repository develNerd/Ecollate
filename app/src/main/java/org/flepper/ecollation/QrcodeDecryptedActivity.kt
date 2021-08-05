package org.flepper.ecollation

import Json4Kotlin_Base
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import coil.api.load
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_qrcode_decrypted.*
import kotlinx.android.synthetic.main.send_votes.*
import java.io.File
import java.io.IOException

class QrcodeDecryptedActivity : AppCompatActivity() {


    var db: FirebaseFirestore? = null

    private var NPP:String? =null
    private var NDC:String? = null
    private var CPP:String? =null
    private var nNPP:String? =null
    private var nNDC:String? = null
    private var nCPP:String? =null
    private var successDialog: Dialog? = null

    private var votes:Votes? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode_decrypted)

       // val imageLoader = Coil.imageLoader(this)

        successDialog = Dialog(this)


        db = FirebaseFirestore.getInstance()

        goBack.setOnClickListener {
            startActivity(Intent(this,ScanDocumentActivity::class.java))
        }


        contin.setOnClickListener {
            showSuccesDialog()
        }



        val info = intent.extras?.getString("info")
        val uri = intent.extras?.getString("uri")


        val json = info
        val topic = Gson().fromJson(json, Json4Kotlin_Base::class.java)
        documentID.text = topic.documentID
        electionYear.text = topic.electionYear
        collationCenter.text = topic.collationCenter
        signature.text = topic.signedBy

        val FILE_PATH = intent.extras?.getString("FILE_PATH")

        // imageViewPreview.load(File(intent.extras?.getString("FILE_PATH")))
            imageViewPreview.load(File(FILE_PATH))

        val image: FirebaseVisionImage
        try {
            if (FILE_PATH != null){
                val savedUri = Uri.fromFile(File(FILE_PATH))
                image = FirebaseVisionImage.fromFilePath(this, savedUri)

                val detector = FirebaseVision.getInstance()
                    .onDeviceTextRecognizer

                val result = detector.processImage(image)
                    .addOnSuccessListener { firebaseVisionText ->

                        val resultText = firebaseVisionText.text
                        for (block in firebaseVisionText.textBlocks) {
                            val blockText = block.text
                            val blockConfidence = block.confidence
                            val blockLanguages = block.recognizedLanguages
                            val blockCornerPoints = block.cornerPoints
                            val blockFrame = block.boundingBox
                            for (line in block.lines) {
                                val lineText = line.text
                                val lineConfidence = line.confidence
                                val lineLanguages = line.recognizedLanguages
                                val lineCornerPoints = line.cornerPoints
                                val lineFrame = line.boundingBox
                                for (element in line.elements) {
                                    val elementText = element.text
                                    val elementConfidence = element.confidence
                                    val elementLanguages = element.recognizedLanguages
                                    val elementCornerPoints = element.cornerPoints
                                    val elementFrame = element.boundingBox
                                }
                            }
                        }

                        var answer = resultText
                        println(answer)
                        answer = answer.replace("[^0-9.]", "") // doesn't work
                        println(answer)
                        val re = Regex("[^0-9.]")
                        answer = re.replace(answer, "") // works

                        val parts = answer.split(".").toTypedArray()
                        try {
                            npp.text = parts[0]
                            ndc.text = parts[1]
                            cpp.text = parts[2]
                            nNPP = parts[0]
                            nNDC = parts[1]
                            nCPP = parts[2]
                             votes = Votes(nNPP!!,nNDC!!,nCPP!!)
                        }catch (e:Exception){
                            toast(e.message.toString())
                        }


                    }
                    .addOnFailureListener { e ->toast(e.message.toString())
                    }


            }
            else{
                toast("Vim Dey")

            }
        } catch (e: IOException) {
            e.printStackTrace()
            toast(e.message.toString())
        }


    }

    override fun onBackPressed() {
        super.onBackPressed()
        toast("please Use in App Buttons")
    }

    private fun writeComments(comment: Votes){
        val votes = HashMap<String, Any>()
        votes["NPP"] = comment.NPP
        votes["NDC"] = comment.NDC
        votes["CPP"] = comment.CPP



        db = FirebaseFirestore.getInstance()

        db?.collection("2020_ELECTIONS")
            ?.document("number_of_votes")
            ?.set(votes)
            ?.addOnSuccessListener {
                toast("Document Added")
                startActivity(Intent(this,PreviewActivity::class.java))
            }
            ?.addOnFailureListener { e ->
                toast("Comment not Sent, $e")

                toast("Try Again")
                //   Log.Timber.e(e, "Error writing document")
            }



    }

    private fun getVotes(){
        db = FirebaseFirestore.getInstance()

        val docRef = db?.collection("2020_ELECTIONS")?.document("number_of_votes")
        docRef?.get()?.addOnSuccessListener { documentSnapshot ->
            val city = documentSnapshot.toObject(Votes::class.java)
            NPP = city?.NPP
            NDC = city?.NDC
            CPP = city?.CPP


        }
            ?.addOnFailureListener {
                toast(it.message.toString())
            }
    }


    private fun showSuccesDialog(){
        val li: LayoutInflater = LayoutInflater.from(this)
        val view: View = li.inflate(R.layout.send_votes, null)
        val btnOkay: Button = view.findViewById(R.id.okay) as Button
        val send: Button = view.findViewById(R.id.sendVotes) as Button

        btnOkay.setOnClickListener {
            startActivity(intent)
            getVotes()
            send.visibility = View.VISIBLE
            btnOkay.visibility = View.GONE

        }
        send.setOnClickListener {
            if (NPP != null){
                val mNPP = (nNPP!!.toInt() + NPP!!.toInt()).toString()
                val mNDC = (nNDC!!.toInt() + nNDC!!.toInt()).toString()
                val mCPP = (nCPP!!.toInt() + CPP!!.toInt()).toString()
                val votes  = Votes(mNPP,mNDC,mCPP
                    )

                writeComments(votes)
            }
        }
        successDialog?.setContentView(view)
        successDialog?.setCanceledOnTouchOutside(false)
        successDialog?.setOnCancelListener {
            successDialog?.dismiss()
        }
        successDialog?.show()

    }

}
