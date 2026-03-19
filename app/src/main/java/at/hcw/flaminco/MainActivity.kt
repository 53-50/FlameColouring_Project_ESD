package at.hcw.flaminco

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        findViewById<Button>(R.id.btnRecordBaseline).setOnClickListener {
            startActivity(Intent(this, BaselineActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewBaseline).setOnClickListener {
            startActivity(Intent(this, BaselineDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnRecordReference).setOnClickListener {
            startActivity(Intent(this, ReferenceActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewReference).setOnClickListener {
            startActivity(Intent(this, ReferenceDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnRecordSample).setOnClickListener {
            startActivity(Intent(this, SampleActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewSample).setOnClickListener {
            startActivity(Intent(this, SampleDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnCompare).setOnClickListener {
            startActivity(Intent(this, ComparisonActivity::class.java))
        }

        findViewById<Button>(R.id.btnQuit).setOnClickListener {
            finish()
        }
    }
}