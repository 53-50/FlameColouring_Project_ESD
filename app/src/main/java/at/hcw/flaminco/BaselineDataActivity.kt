package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class BaselineDataActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_baseline_data)

        findViewById<Button>(R.id.btnBackBaselineData).setOnClickListener {
            finish()
        }
    }
}