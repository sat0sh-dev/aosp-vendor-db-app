package com.example.dbapp

import android.app.Activity
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.vendor.dbclient.DbClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var client: DbClient
    private lateinit var keyInput: EditText
    private lateinit var valueInput: EditText
    private lateinit var resultText: TextView

    companion object {
        private const val TAG = "DbApp"
        // Phase 3.8: OEM Privacy Permission
        private const val PERMISSION_PRIVACY_DATA = "oem.permission.PRIVACY_DATA"
        private const val REQUEST_CODE_PRIVACY = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "=== DbApp Starting ===")
        Log.i(TAG, "Package: ${applicationContext.packageName}")
        Log.i(TAG, "UID: ${android.os.Process.myUid()}")
        Log.i(TAG, "PID: ${android.os.Process.myPid()}")

        // Phase 3.6: パッケージ名を明示的に指定
        client = DbClient(packageName)

        keyInput = findViewById(R.id.key_input)
        valueInput = findViewById(R.id.value_input)
        resultText = findViewById(R.id.result_text)

        findViewById<Button>(R.id.btn_get).setOnClickListener {
            onGetClicked()
        }

        findViewById<Button>(R.id.btn_set).setOnClickListener {
            onSetClicked()
        }

        findViewById<Button>(R.id.btn_list).setOnClickListener {
            onListClicked()
        }

        // Phase 3.10: UDS Test Button (experimental)
        findViewById<Button>(R.id.btn_uds_test)?.setOnClickListener {
            onUdsTestClicked()
        }

        // Phase 3.8: Check and request permission before connecting
        checkAndRequestPermission()
    }

    /**
     * Phase 3.8: Check if PRIVACY_DATA permission is granted
     * If not, request it from the user
     *
     * Note: Server (auth_hook) verifies permission via
     * 'cmd package check-permission' (Treble workaround)
     *
     * Note: Custom permission may not be defined in all builds.
     * If not defined, skip permission check and proceed with attestation only.
     */
    private fun checkAndRequestPermission() {
        Log.i(TAG, "[Phase 3.8] Checking permission: $PERMISSION_PRIVACY_DATA")
        Log.i(TAG, "[Phase 3.8] Note: Server uses command-based check (Treble workaround)")

        // Check if custom permission exists in this build
        try {
            packageManager.getPermissionInfo(PERMISSION_PRIVACY_DATA, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // Permission not defined in this build, skip check
            Log.i(TAG, "[Phase 3.8] Custom permission not defined, skipping permission check")
            startAutoTest()
            return
        }

        if (checkSelfPermission(PERMISSION_PRIVACY_DATA) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "[Phase 3.8] Permission already granted")
            // Permission granted, proceed with auto-test
            startAutoTest()
        } else {
            Log.i(TAG, "[Phase 3.8] Permission not granted, requesting...")
            resultText.text = """
                |[Phase 3.8] Permission Required
                |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                |Requesting: $PERMISSION_PRIVACY_DATA
                |
                |This permission is required for
                |accessing the Data Broker service.
                |
                |Server will verify via command:
                |'cmd package check-permission'
            """.trimMargin()
            requestPermissions(arrayOf(PERMISSION_PRIVACY_DATA), REQUEST_CODE_PRIVACY)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PRIVACY) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "[Phase 3.8] Permission granted by user")
                showToast("Permission granted!")
                // Permission granted, proceed with auto-test
                startAutoTest()
            } else {
                Log.e(TAG, "[Phase 3.8] Permission denied by user")
                resultText.text = "Permission denied.\nCannot access Data Broker without PRIVACY_DATA permission."
                showToast("Permission denied")
            }
        }
    }

    /**
     * Phase 3.8: Start auto-test after permission is granted
     */
    private fun startAutoTest() {
        // Auto-test: Execute LIST operation on startup
        thread {
            try {
                Thread.sleep(1000) // Wait for UI to settle

                // Phase 3.8: Show authentication info
                Log.i(TAG, "=== Phase 3.8: 2-Layer Authentication ===")
                Log.i(TAG, "  Layer 1: Keystore Attestation (X.509 certificate)")
                Log.i(TAG, "  Layer 2: Permission check (command-based)")
                Log.i(TAG, "  Note: Server uses 'cmd package check-permission' (Treble workaround)")

                runOnUiThread {
                    resultText.text = """
                        |[Phase 3.8] 2-Layer Authentication
                        |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        |Layer 1: Keystore Attestation
                        |Layer 2: Permission check
                        |
                        |Note: Server uses command-based
                        |permission check (Treble workaround)
                        |'cmd package check-permission'
                        |
                        |Connecting to Data Broker...
                    """.trimMargin()
                }

                Log.i(TAG, "=== Auto-test: Connecting to Data Broker ===")

                // Phase 3.6.5: Explicitly connect before operations (triggers token generation)
                if (!client.connect()) {
                    Log.e(TAG, "Auto-test: Failed to connect to Data Broker")
                    runOnUiThread {
                        resultText.text = """
                            |[Phase 3.8] Connection Failed
                            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                            |Layer 1: Keystore Attestation - ?
                            |Layer 2: Permission (cmd) - ?
                            |
                            |Error: Could not connect to db_daemon
                            |Check logcat for details:
                            |  adb logcat | grep -E "(auth_hook|db_daemon)"
                        """.trimMargin()
                    }
                    return@thread
                }

                Log.i(TAG, "=== Auto-test: Executing LIST operation ===")
                val data = client.listAsMap()
                Log.i(TAG, "LIST result: ${data.size} entries")
                data.forEach { (key, value) ->
                    Log.i(TAG, "  - $key = $value")
                }

                runOnUiThread {
                    val sb = StringBuilder()
                    sb.append("[Phase 3.8] Authentication Success!\n")
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    sb.append("Layer 1: Keystore Attestation ✓\n")
                    sb.append("Layer 2: Permission (cmd) ✓\n")
                    sb.append("\n")
                    sb.append("Note: Server verified permission\n")
                    sb.append("via 'cmd package check-permission'\n")
                    sb.append("\n")

                    if (data.isEmpty()) {
                        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                        sb.append("Data Broker: Empty (no data)")
                    } else {
                        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                        sb.append("Data (${data.size} entries):\n\n")
                        data.forEach { (key, value) ->
                            sb.append("$key = $value\n")
                        }
                    }
                    resultText.text = sb.toString()
                }
                Log.i(TAG, "=== Auto-test completed successfully ===")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-test failed: ${e.message}", e)
                runOnUiThread {
                    resultText.text = """
                        |[Phase 3.8] Error
                        |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        |${e.message}
                        |
                        |Check logcat:
                        |  adb logcat | grep auth_hook
                    """.trimMargin()
                }
            }
        }
    }

    private fun onGetClicked() {
        val key = keyInput.text.toString()
        if (key.isEmpty()) {
            showToast("Please enter a key")
            return
        }

        // Phase 3.6.5: Ensure connection before operation
        if (!client.isConnected() && !client.connect()) {
            resultText.text = "Failed to connect to Data Broker"
            return
        }

        val value = client.get(key)
        if (value != null && value.isNotEmpty()) {
            resultText.text = "GET $key:\n$value"
        } else {
            resultText.text = "Key '$key' not found"
        }
    }

    private fun onSetClicked() {
        val key = keyInput.text.toString()
        val value = valueInput.text.toString()

        if (key.isEmpty() || value.isEmpty()) {
            showToast("Please enter both key and value")
            return
        }

        // Phase 3.6.5: Ensure connection before operation
        if (!client.isConnected() && !client.connect()) {
            resultText.text = "Failed to connect to Data Broker"
            return
        }

        val success = client.set(key, value)
        if (success) {
            resultText.text = "SET $key=$value:\nSuccess"
            showToast("Data saved successfully")
        } else {
            resultText.text = "SET $key=$value:\nFailed"
            showToast("Failed to save data")
        }
    }

    private fun onListClicked() {
        // Phase 3.6.5: Ensure connection before operation
        if (!client.isConnected() && !client.connect()) {
            resultText.text = "Failed to connect to Data Broker"
            return
        }

        val data = client.listAsMap()

        if (data.isEmpty()) {
            resultText.text = "No data in Data Broker"
            return
        }

        val sb = StringBuilder("All Data (${data.size} entries):\n\n")
        data.forEach { (key, value) ->
            sb.append("$key = $value\n")
        }
        resultText.text = sb.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Phase 3.10: UDS Connection Test (Experimental)
     *
     * Tests direct UDS connection from app to db_daemon.
     * Uses Android's LocalSocket API.
     */
    private fun onUdsTestClicked() {
        Log.i(TAG, "=== Phase 3.10: UDS Connection Test ===")
        resultText.text = "[Phase 3.10] UDS Test\nConnecting..."

        thread {
            try {
                val socketPath = "/data/misc/db/data_broker.sock"
                Log.i(TAG, "Connecting to UDS: $socketPath")

                val socket = LocalSocket()
                val address = LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)

                socket.connect(address)
                Log.i(TAG, "UDS connected!")

                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                // Send AUTH command with token (for Phase 3.10, we still need token auth)
                val token = com.vendor.dbclient.AuthTokenProvider.generateToken(packageName)
                val authCmd = "AUTH $packageName $token"
                Log.i(TAG, "Sending AUTH command (token_len=${token.length})")
                writer.println(authCmd)

                val authResponse = reader.readLine()
                Log.i(TAG, "AUTH response: $authResponse")

                if (authResponse?.startsWith("OK:") != true) {
                    runOnUiThread {
                        resultText.text = """
                            |[Phase 3.10] UDS Test
                            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                            |Connection: ✓ Success
                            |Auth: ✗ Failed
                            |Response: $authResponse
                        """.trimMargin()
                    }
                    socket.close()
                    return@thread
                }

                // Send LIST command
                Log.i(TAG, "Sending LIST command")
                writer.println("LIST")

                val listResponse = reader.readLine()
                Log.i(TAG, "LIST response: $listResponse")

                socket.close()

                runOnUiThread {
                    resultText.text = """
                        |[Phase 3.10] UDS Test SUCCESS!
                        |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        |Socket: $socketPath
                        |Connection: ✓ Success
                        |Auth: ✓ Success
                        |
                        |Data:
                        |$listResponse
                    """.trimMargin()
                }

                Log.i(TAG, "=== UDS Test completed successfully ===")

            } catch (e: Exception) {
                Log.e(TAG, "UDS Test failed: ${e.message}", e)
                runOnUiThread {
                    resultText.text = """
                        |[Phase 3.10] UDS Test FAILED
                        |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        |Error: ${e.message}
                        |
                        |Check:
                        |1. SELinux: adb logcat | grep avc
                        |2. Socket exists: adb shell ls -la /data/misc/db/
                    """.trimMargin()
                }
            }
        }
    }
}
