package com.example

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.room.Room
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.ProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var db: AppDatabase

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    db = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java, "drawing-app-db"
    ).fallbackToDestructiveMigration().build()

    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
        ) { innerPadding ->
          MannequinWebView(modifier = Modifier.padding(innerPadding).fillMaxSize(), db = db)
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MannequinWebView(modifier: Modifier = Modifier, db: AppDatabase) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  val fileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      fileChooserCallback?.onReceiveValue(arrayOf(uri))
    } else {
      fileChooserCallback?.onReceiveValue(null)
    }
    fileChooserCallback = null
  }

  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        webViewClient = WebViewClient()
        
        addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun getAllProjects(): String {
                var jsonArray = JSONArray()
                runBlocking(Dispatchers.IO) {
                    val projects = db.projectDao().getAllProjects()
                    projects.forEach { p ->
                        val obj = JSONObject()
                        obj.put("id", p.id)
                        obj.put("name", p.name)
                        obj.put("thumbnail", p.thumbnail ?: "")
                        obj.put("timestamp", p.timestamp)
                        jsonArray.put(obj)
                    }
                }
                return jsonArray.toString()
            }

            @JavascriptInterface
            fun saveProject(id: Int, name: String, thumbnail: String, stateJson: String): Int {
                var newId = id
                runBlocking(Dispatchers.IO) {
                    val state = ProjectState(
                        id = if (id <= 0) 0 else id, // 0 for auto-generate
                        name = name,
                        thumbnail = thumbnail.ifEmpty { null },
                        stateJson = stateJson
                    )
                    newId = db.projectDao().insertProjectState(state).toInt()
                }
                return newId
            }

            @JavascriptInterface
            fun loadState(id: Int): String? {
                var json: String? = null
                runBlocking(Dispatchers.IO) {
                    json = db.projectDao().getProjectState(id)?.stateJson
                }
                return json
            }
            
            @JavascriptInterface
            fun deleteProject(id: Int) {
                scope.launch(Dispatchers.IO) {
                    db.projectDao().deleteProject(id)
                }
            }

            private var videoOutputStream: java.io.OutputStream? = null

            @JavascriptInterface
            fun startVideoExport(): Boolean {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Proceso_${System.currentTimeMillis()}.webm")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/webm")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/MannequinApp")
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                return if (uri != null) {
                    try {
                        videoOutputStream = resolver.openOutputStream(uri)
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                } else false
            }

            @JavascriptInterface
            fun appendVideoData(base64Chunk: String) {
                try {
                    val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
                    videoOutputStream?.write(bytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            @JavascriptInterface
            fun finishVideoExport() {
                try {
                    videoOutputStream?.close()
                    videoOutputStream = null
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Video exportado a la carpeta Películas", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            @JavascriptInterface
            fun saveImageToGallery(base64Data: String) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "Drawing_${System.currentTimeMillis()}.png")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/MannequinApp")
                        }

                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Imagen exportada a la galería", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error al crear archivo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }, "AndroidApp")

        webChromeClient = object : WebChromeClient() {
          override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
          ): Boolean {
            fileChooserCallback = filePathCallback
            fileChooserLauncher.launch("*/*")
            return true
          }

          override fun onPermissionRequest(request: PermissionRequest) {
            if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
              if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
              } else {
                request.deny()
              }
            } else {
              request.deny()
            }
          }
        }
        loadUrl("file:///android_asset/mannequin.html")
      }
    }
  )
}
