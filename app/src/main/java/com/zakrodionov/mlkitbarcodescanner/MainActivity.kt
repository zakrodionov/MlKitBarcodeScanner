package com.zakrodionov.mlkitbarcodescanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zakrodionov.mlkitbarcodescanner.barcode.BarcodeFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.flContainer, BarcodeFragment()).commit()
        }
    }
}