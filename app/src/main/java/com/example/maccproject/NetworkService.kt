package com.example.maccproject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Query

data class TheftLog(
    val id: String,
    val timestamp: String,
    val image_url: String,
    val lat: Double,
    val lon: Double
)
interface TheftApi {
    @Multipart
    @POST("api/report")
    suspend fun uploadReport(
        @Part photo: MultipartBody.Part,       //  image file
        @Part("user_email") email: RequestBody, // Text data 1
        @Part("lat") lat: RequestBody,          // Text data 2
        @Part("lon") lon: RequestBody,          // Text data 3
        @Part("timestamp") timestamp: RequestBody // Text data 4
    ): Response<ResponseBody>

    @GET("api/my_reports")
    suspend fun getReports(@Query("email") email: String): List<TheftLog>
}

// 2. Create the Singleton
object NetworkManager {
    private const val BASE_URL = "https://lorisabbruzzo.pythonanywhere.com/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val theftApi: TheftApi = retrofit.create(TheftApi::class.java)
}