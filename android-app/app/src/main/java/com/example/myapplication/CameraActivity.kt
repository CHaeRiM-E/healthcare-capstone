package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CameraActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView

    // 분석할 실제 이미지의 Uri
    private var imageUriForAnalysis: Uri? = null

    // 갤러리
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                imageUriForAnalysis = uri
                imagePreview.setImageURI(uri)
            }
        }

    // 카메라
    private val cameraLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

                val bitmap =
                    result.data?.extras?.get("data") as? Bitmap

                if (bitmap != null) {

                    imagePreview.setImageBitmap(bitmap)

                    // 촬영된 Bitmap을 임시 JPG 파일로 저장
                    imageUriForAnalysis = saveBitmapToCache(bitmap)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val btnHome = findViewById<Button>(R.id.btnHome)
        val btnUpload = findViewById<Button>(R.id.btnUpload)
        val btnTakePhoto = findViewById<Button>(R.id.btnCapture)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)

        imagePreview = findViewById(R.id.imagePreview)
        imagePreview.scaleType = ImageView.ScaleType.FIT_CENTER

        // 홈
        btnHome.setOnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java)
            )
        }

        // 갤러리
        btnUpload.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // 카메라
        btnTakePhoto.setOnClickListener {

            val cameraIntent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            cameraLauncher.launch(cameraIntent)
        }

        // 분석하기
        btnAnalyze.setOnClickListener {

            val imageUri = imageUriForAnalysis

            if (imageUri == null) {

                Toast.makeText(
                    this,
                    "먼저 사진을 촬영하거나 선택해주세요.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val intent =
                Intent(this, AnalysisActivity::class.java)

            intent.putExtra(
                "imageUri",
                imageUri.toString()
            )

            startActivity(intent)
        }
    }

    // 카메라 Bitmap → 임시 JPG 파일
    private fun saveBitmapToCache(bitmap: Bitmap): Uri {

        val file =
            File(cacheDir, "injury_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use { outputStream ->

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                90,
                outputStream
            )
        }

        return Uri.fromFile(file)
    }
}