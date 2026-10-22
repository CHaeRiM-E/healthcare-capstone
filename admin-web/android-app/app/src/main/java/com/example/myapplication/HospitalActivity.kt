package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder

class HospitalActivity : AppCompatActivity() {

    private val kakaoRestApiKey: String by lazy {
        getString(R.string.kakao_rest_api_key)
    }

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private lateinit var hospitalInfoCard: View
    private lateinit var txtSelectedHospitalName: TextView
    private lateinit var txtSelectedHospitalAddress: TextView
    private lateinit var txtSelectedHospitalCategory: TextView
    private lateinit var btnSelectedHospitalCall: Button
    private lateinit var btnSelectedHospitalRoute: Button

    private var selectedHospital: Hospital? = null
    private val searchKeyword by lazy { intent.getStringExtra("hospitalSearchKeyword").orEmpty() }
    private val hospitalMarkerMap = mutableMapOf<String, Hospital>()

    private lateinit var txtLocation: TextView
    private lateinit var txtHospitalCount: TextView
    private lateinit var txtEmptyHospital: TextView
    private lateinit var progressHospital: ProgressBar
    private lateinit var recyclerHospitals: RecyclerView

    private val hospitals =
        mutableListOf<Hospital>()

    private lateinit var hospitalAdapter:
            HospitalAdapter

    private val httpClient =
        OkHttpClient()

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val coarseGranted =
                permissions[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (fineGranted || coarseGranted) {

                getCurrentLocation()

            } else {

                progressHospital.visibility =
                    View.GONE

                txtLocation.text =
                    "위치 권한이 허용되지 않았습니다."

                Toast.makeText(
                    this,
                    "주변 병원을 찾으려면 위치 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        KakaoMapSdk.init(
            this,
            getString(R.string.kakao_native_app_key)
        )

        setContentView(
            R.layout.activity_hospital
        )
        hospitalInfoCard = findViewById(R.id.hospitalInfoCard)

        txtSelectedHospitalName =
            findViewById(R.id.txtSelectedHospitalName)

        txtSelectedHospitalAddress =
            findViewById(R.id.txtSelectedHospitalAddress)

        txtSelectedHospitalCategory =
            findViewById(R.id.txtSelectedHospitalCategory)

        btnSelectedHospitalCall =
            findViewById(R.id.btnSelectedHospitalCall)

        btnSelectedHospitalRoute =
            findViewById(R.id.btnSelectedHospitalRoute)
        btnSelectedHospitalCall.setOnClickListener {
            selectedHospital?.let { hospital ->
                openDial(hospital.phone)
            }
        }

        btnSelectedHospitalRoute.setOnClickListener {
            selectedHospital?.let { hospital ->
                openHospitalRoute(hospital)
            }
        }

        findViewById<TextView>(R.id.btnCloseHospitalCard)
            .setOnClickListener {
                hospitalInfoCard.visibility = View.GONE
                selectedHospital = null
            }

        val mapContainer =
            findViewById<android.widget.FrameLayout>(
                R.id.mapContainer
            )

        mapView = MapView(this)
        mapContainer.addView(mapView)

        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    kakaoMap = null
                }

                override fun onMapError(error: Exception) {
                    Log.e("KAKAO_MAP", "지도 오류", error)
                }
            },
            object : KakaoMapReadyCallback() {

                override fun getPosition(): LatLng {
                    return LatLng.from(
                        currentLatitude ?: 37.5614,
                        currentLongitude ?: 127.0377
                    )
                }

                override fun getZoomLevel(): Int {
                    return 16
                }

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    map.setOnLabelClickListener { _, _, label ->
                        val hospital = hospitalMarkerMap[label.labelId]

                        if (hospital != null) {
                            if (intent.getBooleanExtra("selectHospitalMode", false)) {
                                selectHospitalForRecord(hospital)
                                return@setOnLabelClickListener true
                            }
                            selectedHospital = hospital
                            hospitalInfoCard.visibility = View.VISIBLE
                            txtSelectedHospitalName.text = hospital.name
                            txtSelectedHospitalAddress.text = hospital.address
                            txtSelectedHospitalCategory.text = hospital.category
                        }

                        true
                    }
                    showHospitalMarkers()

                    val latitude = currentLatitude
                    val longitude = currentLongitude

                    if (latitude != null && longitude != null) {
                        map.moveCamera(
                            CameraUpdateFactory.newCenterPosition(
                                LatLng.from(latitude, longitude),
                                16
                            )
                        )
                    }
                }
            }
        )

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(this)

        txtLocation =
            findViewById(R.id.txtLocation)

        txtHospitalCount =
            findViewById(R.id.txtHospitalCount)

        txtEmptyHospital =
            findViewById(R.id.txtEmptyHospital)

        progressHospital =
            findViewById(R.id.progressHospital)

        recyclerHospitals =
            findViewById(R.id.recyclerHospitals)

        val btnHome =
            findViewById<Button>(R.id.btnHome)

        val btnBack =
            findViewById<Button>(R.id.btnBack)

        val btnRefreshLocation =
            findViewById<Button>(
                R.id.btnRefreshLocation
            )

        val btnOpenMap =
            findViewById<Button>(
                R.id.btnOpenMap
            )

        hospitalAdapter =
            HospitalAdapter(
                hospitals = hospitals,

                onRouteClick = { hospital ->
                    openHospitalRoute(hospital)
                },

                onCallClick = { hospital ->
                    openDial(hospital.phone)
                },


                onHospitalClick = { hospital ->

                    if (intent.getBooleanExtra("selectHospitalMode", false)) {

                        // 진료 기록에서 병원을 선택한 경우
                        selectHospitalForRecord(hospital)

                    } else {

                        // 기존 병원 찾기 기능 유지
                        kakaoMap?.moveCamera(
                            CameraUpdateFactory.newCenterPosition(
                                LatLng.from(
                                    hospital.latitude,
                                    hospital.longitude
                                ),
                                17
                            ),
                            CameraAnimation.from(500)
                        )
                    }
                }
            )

        recyclerHospitals.layoutManager =
            LinearLayoutManager(this)

        recyclerHospitals.adapter =
            hospitalAdapter

        btnHome.setOnClickListener {

            val intent =
                Intent(
                    this,
                    HomeActivity::class.java
                )

            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP

            startActivity(intent)
            finish()
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnRefreshLocation.setOnClickListener {
            checkLocationPermission()
        }

        txtLocation.setOnClickListener {
            checkLocationPermission()
        }

        btnOpenMap.setOnClickListener {
            openNearbyHospitalMap()
        }

        checkLocationPermission()
    }

    private fun checkLocationPermission() {

        val finePermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        val coarsePermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        if (
            finePermission ==
            PackageManager.PERMISSION_GRANTED ||
            coarsePermission ==
            PackageManager.PERMISSION_GRANTED
        ) {

            getCurrentLocation()

        } else {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {

        progressHospital.visibility =
            View.VISIBLE

        txtEmptyHospital.visibility =
            View.GONE

        recyclerHospitals.visibility =
            View.GONE

        txtLocation.text =
            "📍 현재 위치를 확인하고 있습니다."

        val cancellationTokenSource =
            CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->

            if (location == null) {

                progressHospital.visibility =
                    View.GONE

                txtLocation.text =
                    "현재 위치를 찾을 수 없습니다."

                Toast.makeText(
                    this,
                    "에뮬레이터 또는 휴대폰의 위치를 켜주세요.",
                    Toast.LENGTH_LONG
                ).show()

                return@addOnSuccessListener
            }

            currentLatitude =
                location.latitude

            currentLongitude =
                location.longitude
            kakaoMap?.moveCamera(
                CameraUpdateFactory.newCenterPosition(
                    LatLng.from(location.latitude, location.longitude),
                    16
                ),
                CameraAnimation.from(500)
            )

            loadCurrentRegion(
                latitude = location.latitude,
                longitude = location.longitude
            )

            searchNearbyHospitals(
                latitude = location.latitude,
                longitude = location.longitude
            )

        }.addOnFailureListener {

            progressHospital.visibility =
                View.GONE

            txtLocation.text =
                "위치 확인에 실패했습니다."

            Toast.makeText(
                this,
                "현재 위치 확인에 실패했습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadCurrentRegion(
        latitude: Double,
        longitude: Double
    ) {

        if (kakaoRestApiKey.isBlank()) {
            txtLocation.text = "📍 현재 위치 확인됨"
            return
        }

        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host("dapi.kakao.com")
                .addPathSegments(
                    "v2/local/geo/coord2regioncode.json"
                )
                .addQueryParameter(
                    "x",
                    longitude.toString()
                )
                .addQueryParameter(
                    "y",
                    latitude.toString()
                )
                .build()

        val request =
            Request.Builder()
                .url(url)
                .addHeader(
                    "Authorization",
                    "KakaoAK $kakaoRestApiKey"
                )
                .get()
                .build()

        httpClient.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {

                    Log.e(
                        "KAKAO_REGION",
                        "지역명 조회 연결 실패",
                        e
                    )

                    runOnUiThread {
                        txtLocation.text =
                            "📍 현재 위치 확인됨"
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {

                    response.use {

                        val body =
                            response.body?.string().orEmpty()

                        Log.d(
                            "KAKAO_REGION",
                            "code=${response.code}, body=$body"
                        )

                        if (!response.isSuccessful) {

                            runOnUiThread {
                                txtLocation.text =
                                    "📍 현재 위치 확인됨"
                            }

                            return
                        }

                        try {

                            val documents =
                                JSONObject(body)
                                    .getJSONArray("documents")

                            if (documents.length() == 0) {

                                runOnUiThread {
                                    txtLocation.text =
                                        "📍 현재 위치 확인됨"
                                }

                                return
                            }

                            var selectedRegion =
                                documents.getJSONObject(0)

                            for (
                            index in
                            0 until documents.length()
                            ) {

                                val region =
                                    documents.getJSONObject(index)

                                if (
                                    region.optString(
                                        "region_type"
                                    ) == "H"
                                ) {
                                    selectedRegion = region
                                    break
                                }
                            }

                            val region1 =
                                selectedRegion.optString(
                                    "region_1depth_name"
                                )

                            val region2 =
                                selectedRegion.optString(
                                    "region_2depth_name"
                                )

                            val region3 =
                                selectedRegion.optString(
                                    "region_3depth_name"
                                )

                            val regionText =
                                listOf(
                                    region1,
                                    region2,
                                    region3
                                )
                                    .filter {
                                        it.isNotBlank()
                                    }
                                    .joinToString(" ")

                            runOnUiThread {

                                txtLocation.text =
                                    if (regionText.isNotBlank()) {
                                        "📍 현재 위치\n$regionText"
                                    } else {
                                        "📍 현재 위치 확인됨"
                                    }
                            }

                        } catch (e: Exception) {

                            Log.e(
                                "KAKAO_REGION",
                                "지역명 결과 해석 실패",
                                e
                            )

                            runOnUiThread {
                                txtLocation.text =
                                    "📍 현재 위치 확인됨"
                            }
                        }
                    }
                }
            }
        )
    }

    private fun searchNearbyHospitals(
        latitude: Double,
        longitude: Double
    ) {

        if (kakaoRestApiKey.isBlank()) {

            progressHospital.visibility =
                View.GONE

            txtEmptyHospital.visibility =
                View.VISIBLE

            txtEmptyHospital.text =
                "카카오 REST API 키가 설정되지 않았습니다."

            return
        }

        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host("dapi.kakao.com")
                .addPathSegments(
                    if (searchKeyword.isNotBlank())
                        "v2/local/search/keyword.json" else "v2/local/search/category.json"
                )
                .apply {
                    if (searchKeyword.isNotBlank()) addQueryParameter("query", searchKeyword)
                    else addQueryParameter("category_group_code", "HP8")
                }
                .addQueryParameter(
                    "x",
                    longitude.toString()
                )
                .addQueryParameter(
                    "y",
                    latitude.toString()
                )
                .addQueryParameter(
                    "radius",
                    "5000"
                )
                .addQueryParameter(
                    "sort",
                    "distance"
                )
                .addQueryParameter(
                    "size",
                    "15"
                )
                .build()

        val request =
            Request.Builder()
                .url(url)
                .addHeader(
                    "Authorization",
                    "KakaoAK $kakaoRestApiKey"
                )
                .get()
                .build()

        httpClient.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {

                    runOnUiThread {

                        progressHospital.visibility =
                            View.GONE

                        recyclerHospitals.visibility =
                            View.GONE

                        txtEmptyHospital.visibility =
                            View.VISIBLE

                        txtEmptyHospital.text =
                            "병원 정보를 불러오지 못했습니다.\n" +
                                    "인터넷 연결을 확인해주세요."
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {

                    response.use {

                        if (!response.isSuccessful) {

                            runOnUiThread {

                                progressHospital.visibility =
                                    View.GONE

                                recyclerHospitals.visibility =
                                    View.GONE

                                txtEmptyHospital.visibility =
                                    View.VISIBLE

                                txtEmptyHospital.text =
                                    "병원 검색에 실패했습니다.\n" +
                                            "오류 코드: ${response.code}"
                            }

                            return
                        }

                        val body =
                            response.body?.string()
                                ?: return

                        val documents =
                            JSONObject(body)
                                .getJSONArray("documents")

                        val newHospitals =
                            mutableListOf<Hospital>()

                        for (
                        index in
                        0 until documents.length()
                        ) {

                            val item =
                                documents.getJSONObject(index)

                            val roadAddress =
                                item.optString(
                                    "road_address_name"
                                )

                            val address =
                                if (roadAddress.isNotBlank()) {
                                    roadAddress
                                } else {
                                    item.optString(
                                        "address_name"
                                    )
                                }

                            val hospital =
                                Hospital(
                                    name =
                                        item.optString(
                                            "place_name"
                                        ),

                                    address = address,

                                    phone =
                                        item.optString(
                                            "phone"
                                        ),

                                    distance =
                                        item.optString(
                                            "distance"
                                        )
                                            .toIntOrNull()
                                            ?: 0,

                                    category =
                                        item.optString(
                                            "category_name"
                                        ),

                                    longitude =
                                        item.optString("x")
                                            .toDoubleOrNull()
                                            ?: 0.0,

                                    latitude =
                                        item.optString("y")
                                            .toDoubleOrNull()
                                            ?: 0.0
                                )

                            newHospitals.add(hospital)
                        }

                        runOnUiThread {

                            progressHospital.visibility =
                                View.GONE

                            hospitals.clear()
                            hospitals.addAll(newHospitals)

                            hospitalAdapter.notifyDataSetChanged()
                            showHospitalMarkers()

                            txtHospitalCount.text =
                                "주변 병원 ${hospitals.size}곳"

                            if (hospitals.isEmpty()) {

                                recyclerHospitals.visibility =
                                    View.GONE

                                txtEmptyHospital.visibility =
                                    View.VISIBLE

                                txtEmptyHospital.text =
                                    "5km 이내에서 병원을 찾지 못했습니다."

                            } else {

                                txtEmptyHospital.visibility =
                                    View.GONE

                                recyclerHospitals.visibility =
                                    View.VISIBLE
                            }
                        }
                    }
                }
            }
        )
    }

    private fun showHospitalMarkers() {
        val map = kakaoMap ?: return


        // 검색된 병원 위치에 마커 표시
        hospitals.forEach { hospital ->
            if (hospital.latitude != 0.0 &&
                hospital.longitude != 0.0
            ) {
                val position = LatLng.from(
                    hospital.latitude,
                    hospital.longitude
                )

                val options = LabelOptions.from(position)
                    .setStyles(
                        LabelStyles.from(
                            LabelStyle.from(
                                R.drawable.hospital_pin
                            ).setTextStyles(
                                20,
                                android.graphics.Color.BLACK,
                                4,
                                android.graphics.Color.WHITE
                            )
                        )
                    )
                    .setTexts(LabelTextBuilder().setTexts(hospital.name))

                val label = map.labelManager?.layer?.addLabel(options)
                if (label != null) {
                    hospitalMarkerMap[label.labelId] = hospital
                }

                Log.d(
                    "HOSPITAL_MARKER",
                    "병원=${hospital.name}, 생성성공=${label != null}"
                )
            }
        }
    }

    private fun openDial(
        phone: String
    ) {

        if (phone.isBlank()) {

            Toast.makeText(
                this,
                "등록된 전화번호가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val cleanPhone =
            phone.replace("-", "")
                .replace(" ", "")

        val intent =
            Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:$cleanPhone")
            )

        startActivity(intent)
    }

    private fun openHospitalRoute(
        hospital: Hospital
    ) {

        val hospitalName =
            Uri.encode(hospital.name)

        val uri =
            Uri.parse(
                "geo:0,0?q=" +
                        "${hospital.latitude}," +
                        "${hospital.longitude}" +
                        "($hospitalName)"
            )

        openMapIntent(uri)
    }

    private fun openNearbyHospitalMap() {

        val latitude =
            currentLatitude

        val longitude =
            currentLongitude

        if (
            latitude == null ||
            longitude == null
        ) {

            Toast.makeText(
                this,
                "현재 위치를 먼저 확인해주세요.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val keyword =
            Uri.encode("병원")

        val uri =
            Uri.parse(
                "geo:$latitude,$longitude?q=$keyword"
            )

        openMapIntent(uri)
    }

    private fun openMapIntent(
        uri: Uri
    ) {

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )

            startActivity(intent)

        } catch (
            exception: ActivityNotFoundException
        ) {

            Toast.makeText(
                this,
                "사용할 수 있는 지도 앱이 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun selectHospitalForRecord(hospital: Hospital) {
        val result = Intent().apply {
            putExtra("hospitalName", hospital.name)
            putExtra("hospitalAddress", hospital.address)
            putExtra("hospitalPhone", hospital.phone)
            putExtra("hospitalCategory", hospital.category)
            putExtra("hospitalLatitude", hospital.latitude)
            putExtra("hospitalLongitude", hospital.longitude)
        }

        setResult(RESULT_OK, result)
        finish()
    }
}
