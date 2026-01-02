package app.luzzy.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initActivity()
    }

    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
