package curso.carlos.indrive

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import org.w3c.dom.Text

class HistoryActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val service = findViewById<TextView>(R.id.tv_service)
        val carefare = findViewById<TextView>(R.id.tv_carefare)
        val origin = findViewById<TextView>(R.id.tv_origin)
        val destination = findViewById<TextView>(R.id.tv_destination)

        service.text = intent.getStringExtra("history_service_id")
        carefare.text = intent.getIntExtra("history_carefare", 0).toString()
        origin.text = "${intent.getStringExtra("history_origin_lat")}, ${intent.getStringExtra("history_origin_lon")}"
        destination.text = "${intent.getStringExtra("history_destination_lat")}, ${intent.getStringExtra("history_destination_lon")}"
    }
}
