package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ReferenceDataActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reference_data)

        findViewById<Button>(R.id.btnBackReferenceData).setOnClickListener {
            finish()
        }
    }
}