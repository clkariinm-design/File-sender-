package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.ShareableItem
import com.example.data.model.TransferRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class EmbeddedHttpServer(
    private val context: Context,
    private val onFileUploaded: suspend (TransferRecordEntity) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var sharedFiles: List<ShareableItem> = emptyList()

    fun updateSharedFiles(files: List<ShareableItem>) {
        this.sharedFiles = files
    }

    fun startServer(port: Int = TransferProtocol.WEB_PORT) {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                val server = ServerSocket(port)
                server.reuseAddress = true
                serverSocket = server

                while (isActive && isRunning && !server.isClosed) {
                    try {
                        val client = server.accept()
                        scope.launch {
                            handleHttpClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopServer()
            }
        }
    }

    private suspend fun handleHttpClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                val idx = headerLine.indexOf(':')
                if (idx != -1) {
                    headers[headerLine.substring(0, idx).trim().lowercase()] = headerLine.substring(idx + 1).trim()
                }
                headerLine = reader.readLine()
            }

            if (method.equals("GET", ignoreCase = true)) {
                if (path == "/" || path.startsWith("/?")) {
                    serveIndexHtml(output)
                } else if (path.startsWith("/download")) {
                    val fileId = getQueryParam(path, "id")
                    val item = sharedFiles.find { it.id == fileId }
                    if (item != null) {
                        serveFileDownload(item, output)
                    } else {
                        serveNotFound(output)
                    }
                } else {
                    serveNotFound(output)
                }
            } else if (method.equals("POST", ignoreCase = true) && path.startsWith("/upload")) {
                handleFileUpload(headers, input, output)
            } else {
                serveNotFound(output)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun serveIndexHtml(output: OutputStream) {
        val deviceName = NetworkUtils.getDeviceName(context)
        val fileItemsHtml = StringBuilder()

        if (sharedFiles.isEmpty()) {
            fileItemsHtml.append("""<div class="empty-state">No files queued for web download. Select files in XShare app.</div>""")
        } else {
            for (item in sharedFiles) {
                fileItemsHtml.append(
                    """
                    <div class="file-card">
                        <div class="file-info">
                            <div class="file-name">${escapeHtml(item.name)}</div>
                            <div class="file-meta">${item.formattedSize} &bull; ${item.category.title}</div>
                        </div>
                        <a href="/download?id=${item.id}" class="btn-download" download="${escapeHtml(item.name)}">Download</a>
                    </div>
                    """.trimIndent()
                )
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>XShare Offline Web Portal</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background-color: #0B0E14;
                        color: #E2E8F0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        padding: 24px;
                        min-height: 100vh;
                    }
                    .container { max-width: 640px; margin: 0 auto; }
                    .header {
                        text-align: center;
                        margin-bottom: 32px;
                        padding: 24px;
                        background: rgba(26, 34, 51, 0.7);
                        border-radius: 20px;
                        border: 1px solid rgba(0, 242, 254, 0.3);
                        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
                    }
                    .logo {
                        font-size: 28px;
                        font-weight: 800;
                        background: linear-gradient(135deg, #00F2FE 0%, #9D4EDD 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 8px;
                    }
                    .subtitle { font-size: 14px; color: #94A3B8; }
                    .section-title { font-size: 18px; font-weight: 600; margin: 24px 0 12px; color: #00F2FE; }
                    .file-card {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 16px;
                        margin-bottom: 12px;
                        background: rgba(26, 34, 51, 0.5);
                        border: 1px solid rgba(255, 255, 255, 0.1);
                        border-radius: 14px;
                    }
                    .file-info { flex: 1; margin-right: 16px; overflow: hidden; }
                    .file-name { font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                    .file-meta { font-size: 12px; color: #94A3B8; margin-top: 4px; }
                    .btn-download {
                        background: linear-gradient(135deg, #00F2FE, #0099FF);
                        color: #0B0E14;
                        text-decoration: none;
                        font-weight: 700;
                        font-size: 13px;
                        padding: 10px 18px;
                        border-radius: 10px;
                        box-shadow: 0 0 15px rgba(0, 242, 254, 0.4);
                    }
                    .upload-box {
                        background: rgba(26, 34, 51, 0.6);
                        border: 2px dashed rgba(157, 78, 221, 0.5);
                        border-radius: 16px;
                        padding: 24px;
                        text-align: center;
                    }
                    .upload-box input { display: block; margin: 12px auto; color: #CBD5E1; }
                    .btn-upload {
                        background: linear-gradient(135deg, #9D4EDD, #7928CA);
                        color: white;
                        border: none;
                        font-weight: 700;
                        font-size: 14px;
                        padding: 12px 24px;
                        border-radius: 10px;
                        cursor: pointer;
                    }
                    .empty-state { text-align: center; color: #64748B; padding: 24px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">⚡ XShare Offline Portal</div>
                        <div class="subtitle">Connected to <strong>$deviceName</strong></div>
                    </div>

                    <div class="section-title">Available Files to Download (${sharedFiles.size})</div>
                    $fileItemsHtml

                    <div class="section-title">Send Files to Phone</div>
                    <form class="upload-box" method="POST" action="/upload" enctype="multipart/form-data">
                        <div>Upload any photo, document, or video directly to this Android device.</div>
                        <input type="file" name="uploadFile" required />
                        <button type="submit" class="btn-upload">Send to Device</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()

        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun serveFileDownload(item: ShareableItem, output: OutputStream) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(item.uri)
        if (inputStream == null) {
            serveNotFound(output)
            return
        }

        inputStream.use { stream ->
            val safeName = item.name.replace("\"", "")
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${item.mimeType}\r\n" +
                    "Content-Length: ${item.size}\r\n" +
                    "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
                    "Connection: close\r\n\r\n"

            output.write(header.toByteArray(StandardCharsets.UTF_8))
            val buffer = ByteArray(TransferProtocol.BUFFER_SIZE)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    private suspend fun handleFileUpload(headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val contentType = headers["content-type"] ?: ""
        val boundary = contentType.substringAfter("boundary=", "").trim()
        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

        val downloadDir = try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XShare")
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (e: Exception) {
            context.filesDir
        }

        val targetFile = File(downloadDir, "WebUpload_${System.currentTimeMillis()}.bin")
        var bytesWritten = 0L

        FileOutputStream(targetFile).use { fos ->
            val buffer = ByteArray(TransferProtocol.BUFFER_SIZE)
            var totalRead = 0L
            while (totalRead < contentLength) {
                val toRead = Math.min(buffer.size.toLong(), contentLength - totalRead).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                totalRead += read
                bytesWritten += read
            }
            fos.flush()
        }

        val record = TransferRecordEntity(
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            fileSize = bytesWritten,
            mimeType = "application/octet-stream",
            isIncoming = true,
            peerName = "Web Browser Client",
            peerAddress = "Local Wi-Fi",
            bytesTransferred = bytesWritten,
            speedBytesPerSec = 20L * 1024 * 1024,
            status = "COMPLETED"
        )
        onFileUploaded(record)

        val successHtml = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>body{background:#0B0E14;color:#00F2FE;font-family:sans-serif;text-align:center;padding:50px;}</style></head>
            <body><h2>✅ File Uploaded Successfully!</h2><p>Saved to Android Downloads/XShare.</p><br><a href="/" style="color:#A855F7;">Back to Portal</a></body></html>
        """.trimIndent()

        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${successHtml.length}\r\nConnection: close\r\n\r\n$successHtml"
        output.write(resp.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun serveNotFound(output: OutputStream) {
        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(notFound.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun getQueryParam(path: String, key: String): String? {
        val query = path.substringAfter('?', "")
        if (query.isEmpty()) return null
        val pairs = query.split('&')
        for (pair in pairs) {
            val parts = pair.split('=')
            if (parts.size == 2 && parts[0] == key) {
                return URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return null
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (ignored: Exception) {}
    }
}
