package at.hcw.flaminco

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ReferenceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reference)

        findViewById<Button>(R.id.btnBackReference).setOnClickListener {
            finish()
        }
    }
}