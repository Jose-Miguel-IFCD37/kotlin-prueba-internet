package com.visualstudioex3.pruebainternet

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.visualstudioex3.pruebainternet.ui.theme.PruebaInternetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private val baseModifier = Modifier
        .fillMaxSize()
        .padding(16.dp)

    /*
        Este objeto permite guardar los estados mutables de la composicion generada por el metodo
        TestCaseSectionRow: el valor del TextField y el estado activo del boton.

        Esta implementado basandose en el codigo del objeto TextFieldState de Jetpack Compose.
     */
    class TestCaseSectionRowState {
        val fieldState: TextFieldState
        var buttonEnabled by mutableStateOf(true)

        @RememberInComposition
        constructor(
            fieldState: TextFieldState,
            buttonEnabled: Boolean
        ) {
            this@TestCaseSectionRowState.fieldState = fieldState
            this@TestCaseSectionRowState.buttonEnabled = buttonEnabled
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PruebaInternetTheme {
                Scaffold(
                    modifier = baseModifier
                ) { innerPadding ->
                    NetworkTest(Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Composable
    fun NetworkTest(modifier: Modifier) {
        Column {
            TestCaseSectionRow(
                modifier,
                "Comprobar conexión a internet",
                buttonLabel = "Consultar estado red",
                fieldLabel = "Estado conexión de red",
                fieldPlaceHolder = "Desconocido",
                testCase = {
                    if (isInternetAvailable())
                        "Disponible"
                    else
                        "No disponible"
                }
            )

            TestCaseSectionRow(
                modifier,
                "Comprobar conexión a internet via URL",
                buttonLabel = "Intentar conectar a la URL",
                fieldLabel = "Estado conexión de red",
                fieldPlaceHolder = "Desconocido",
                testCase = { state ->
                    CoroutineScope(Dispatchers.Main).launch {
                        state.buttonEnabled = false

                        val isInternetAvailable: Boolean = withContext(Dispatchers.IO) {
                            tryConnectToUrl(
                                url = "https://clients3.google.com/generate_204",
                                expectedResponseCode = 204
                            ).first
                        }

                        state.fieldState.setTextAndPlaceCursorAtEnd(
                            if (isInternetAvailable)
                                "Disponible"
                            else
                                "No disponible"
                        )
                        state.buttonEnabled = true
                    }

                    return@TestCaseSectionRow "Conectando..."
                }
            )

            TestCaseSectionRow(
                modifier,
                "Intentar conectar a google.com",
                buttonLabel = "Conectar a google.com",
                fieldLabel = "Codigo de estado HTTP",
                fieldPlaceHolder = "Desconocido",
                testCase = { state ->
                    CoroutineScope(Dispatchers.Main).launch {
                        state.buttonEnabled = false

                        val httpResponseCode: Int = withContext(Dispatchers.IO) {
                            connectToUrl("https://www.google.com/")
                        }

                        state.fieldState.setTextAndPlaceCursorAtEnd(httpResponseCode.toString())
                        state.buttonEnabled = true
                    }

                    return@TestCaseSectionRow "Conectando..."
                }
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun NetworkTestPreview() {
        PruebaInternetTheme {
            NetworkTest(baseModifier)
        }
    }

    @Composable
    fun TestCaseSectionRow(
        modifier: Modifier,
        title: String,
        buttonLabel: String,
        fieldLabel: String,
        fieldPlaceHolder: String,
        testCase: (TestCaseSectionRowState) -> String
    ) {
        val result: TextFieldState = rememberTextFieldState(fieldPlaceHolder)
        val uiState by remember {
            mutableStateOf(
                TestCaseSectionRowState(
                    result,
                    true
                )
            )
        }

        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium
            )

            HorizontalDivider()

            Row(
                modifier,
                Arrangement.spacedBy(4.dp),
                Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        uiState.fieldState.setTextAndPlaceCursorAtEnd(testCase(uiState))
                    },
                    Modifier.defaultMinSize(minHeight = TextFieldDefaults.MinHeight),
                    enabled = uiState.buttonEnabled
                ) {
                    Text(buttonLabel)
                }

                TextField(
                    state = uiState.fieldState,
                    label = { Text(fieldLabel) },
                    readOnly = true,
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
        }
    }

    // Metodo no obsoleto por Google para realizar consultas de red en Android en las versiones
    // actuales del SDK:
    fun isInternetAvailable(): Boolean {
        val connectivityManager = this.getSystemService(
            CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        val currentNetwork: Network? = connectivityManager.activeNetwork
        val caps: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(currentNetwork)

        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
    }

    // No llamarse desde el hilo principal (NetworkOnMainThreadException):
    fun connectToUrl(
        url: String,
        timeOut: Int = 3000
    ): Int {
        return try {
            val urlObj = URL(url)
            val connection = urlObj.openConnection() as HttpURLConnection

            connection.connectTimeout = timeOut
            connection.connect()

            Log.d("URL_REQUEST", "Response code: ${connection.responseCode}")

            connection.responseCode
        } catch (e: Exception) {
            Log.e("URL_REQUEST", "Error al realizar la conexion HTTP: $e")

            -1 // Devolvemos un valor negativo como codigo respuesta HTTP en caso de excepcion.
        }
    }

    // No llamarse desde el hilo principal (NetworkOnMainThreadException):
    fun tryConnectToUrl(
        url: String,
        timeOut: Int = 3000,
        expectedResponseCode: Int = 200
    ): Pair<Boolean, Int> { // Pair<T1, T2> => Tupla de dos valores.
        val responseCode = connectToUrl(url, timeOut)

        return Pair(
            responseCode == expectedResponseCode,
            responseCode
        )
    }

    /*
        Similar a la implementacion de un metodo async en C#:
        Corrutina que se ejecuta asincronamente bloqueando el hilo que la llama hasta finalizar pero
        permitiendo al resto de hilos seguir trabajando.

        Devuelve Deferred<T> => Es un simil de Task<T> de .NET.

        tryConnectToUrlAsync().await() => Similar al operador await de .NET para metodos async pero
        requiere runBlockling (lo cual si se ejecuta desde desde la UI o el hilo principal nos lo
        bloqueara hasta que la corrutina devuelva el resultado).
    */
    @OptIn(DelicateCoroutinesApi::class)
    fun tryConnectToUrlAsync(
        url: String,
        timeOut: Int = 3000,
        expectedResponseCode: Int = 200
    ): Deferred<Pair<Boolean, Int>> = GlobalScope.async {
        return@async tryConnectToUrl(url, timeOut, expectedResponseCode)
    }

    /*
        GlobalScope.launch => Seria el atajo para el siguiente codigo en una corrutina:

        CoroutineScope(Dispatchers.Main).launch {
            ...
        }

        Pero a diferencia de GlobalScope.async este no permite devolver valores (se devuelve siempre
        un Job, similar a un Task de .NET, una tarea independiente que no espera devolver valores).
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun tryConnectToUrlJob(
        url: String,
        timeOut: Int = 3000,
        expectedResponseCode: Int = 200
    ): Job = GlobalScope.launch {
        val success = tryConnectToUrl(url, timeOut, expectedResponseCode)

        Log.d("URL_REQUEST", "Conexion establecida: ${success.first} " +
                "(Codigo de estado recibido: ${success.second})")
    }
}
