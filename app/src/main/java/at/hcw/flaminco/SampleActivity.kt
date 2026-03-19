package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        findViewById<Button>(R.id.btnBackSample).setOnClickListener {
            finish()
        }
    }
}