package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.R

class BaselineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_baseline)

        findViewById<Button>(R.id.btnBackBaseline).setOnClickListener {
            finish()
        }
    }
}