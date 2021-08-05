package org.flepper.ecollation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import coil.api.load
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_preview.*
import java.io.File
import kotlin.system.exitProcess

class PreviewActivity : AppCompatActivity() {


    var db: FirebaseFirestore? = null

    private var NPP:String? =null
    private var NDC:String? = null
    private var CPP:String? =null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        getVotes()

        db = FirebaseFirestore.getInstance()


        contin.setOnClickListener {
            exitProcess(0);
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

            npp.text = NPP
            cpp.text = CPP
            ndc.text = NDC


        }
            ?.addOnFailureListener {
                toast(it.message.toString())
            }
    }
}
