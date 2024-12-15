package la.nisa.qrcodecapture

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.cdimascio.dotenv.dotenv
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class NetworkManager {
    private val httpClient = OkHttpClient()
    private val env = dotenv {
        directory = "/assets"
        filename = "env"
    }
    private val jsonType = "application/json".toMediaType()

    fun send(data: String) {
        println("New URL: $data")
        val jsonData = JSONObject()
        jsonData.put("token", env.get("SET_TOKEN"))
        jsonData.put(env.get("KEY"), data)

        val body = jsonData.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(env.get("KV_STORE_URL"))
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                FirebaseCrashlytics.getInstance().recordException(e);
            }

            override fun onResponse(call: Call, response: Response) {
                println("Response: ${response.isSuccessful}")
            }
        })
    }
}