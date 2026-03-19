package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SampleDataActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_data)

        findViewById<Button>(R.id.btnBackSampleData).setOnClickListener {
            finish()
        }
    }
}