package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.R

class ComparisonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparison)

        findViewById<Button>(R.id.btnBackComparison).setOnClickListener {
            finish()
        }
    }
}