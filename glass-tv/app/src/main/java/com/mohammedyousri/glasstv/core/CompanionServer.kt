package com.mohammedyousri.glasstv.core

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * A tiny LAN-only web page for typing playlist details on a phone instead of
 * with the remote. It only runs while the "Add from Phone" dialog is open and
 * only answers on a random path shown on the TV.
 */
class CompanionServer(private val onSubmit: (Map<String, String>) -> Unit) {
    val token: String = buildString {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        repeat(6) { append(chars[rnd.nextInt(chars.length)]) }
    }

    @Volatile private var socket: ServerSocket? = null

    val port: Int get() = socket?.localPort ?: 0

    fun url(): String? {
        val ip = lanAddress() ?: return null
        if (port == 0) return null
        return "http://$ip:$port/$token"
    }

    fun start() {
        val s = (8686..8690).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
        } ?: ServerSocket(0)
        socket = s
        thread(name = "glass-companion", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { client.use(::handle) } }
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 20_000
        val input = client.getInputStream().buffered()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        var length = 0
        while (true) {
            val h = readLine(input) ?: return
            if (h.isEmpty()) break
            if (h.startsWith("content-length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val out = client.getOutputStream()
        if (path.trim('/') != token) {
            respond(out, 404, "text/plain", "Not found")
            return
        }
        when (method) {
            "GET" -> respond(out, 200, "text/html; charset=utf-8", PAGE)
            "POST" -> {
                if (length <= 0 || length > MAX_BODY) {
                    respond(out, 413, "text/plain", "Too large")
                    return
                }
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val form = parseForm(String(body, 0, read, Charsets.UTF_8))
                onSubmit(form)
                respond(out, 200, "text/plain; charset=utf-8", "OK")
            }
            else -> respond(out, 405, "text/plain", "Method not allowed")
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val MAX_BODY = 40 * 1024 * 1024

        fun parseForm(body: String): Map<String, String> = body.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }

        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        fun qr(text: String, size: Int = 360): Bitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }

        private val PAGE = """
<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Glass TV</title>
<style>
:root{color-scheme:dark}
body{margin:0;font:16px -apple-system,system-ui,Segoe UI,Roboto,sans-serif;background:#0b0b10;color:#fff}
main{max-width:520px;margin:0 auto;padding:28px 18px 60px}
h1{font-size:28px;margin:0 0 4px}p{color:#a3a3ad;margin:0 0 22px}
.tabs{display:flex;gap:6px;background:#1c1c22;border-radius:999px;padding:4px;margin-bottom:20px}
.tabs button{flex:1;border:0;border-radius:999px;padding:10px 0;background:none;color:#a3a3ad;font:inherit;font-weight:600}
.tabs button.on{background:#fff;color:#000}
label{display:block;font-size:13px;color:#a3a3ad;margin:14px 0 6px}
input{width:100%;box-sizing:border-box;padding:13px 14px;border-radius:12px;border:1px solid #2c2c34;background:#16161c;color:#fff;font:inherit}
.go{width:100%;margin-top:24px;padding:14px;border:0;border-radius:14px;background:#0a84ff;color:#fff;font:inherit;font-weight:700}
.panel{display:none}.panel.on{display:block}#msg{margin-top:18px;text-align:center;color:#30d158;font-weight:600}
</style></head><body><main>
<h1>Glass TV</h1><p>Add your IPTV provider. It will appear on your TV right away.</p>
<div class="tabs"><button data-p="m3u" class="on">Playlist link</button><button data-p="xtream">Xtream login</button><button data-p="file">File</button></div>
<form id="f">
<div class="panel on" id="m3u"><label>Playlist URL (M3U)</label><input name="url" type="url" placeholder="https://provider.example/playlist.m3u">
<label>Guide URL (XMLTV, optional)</label><input name="epg" type="url" placeholder="https://provider.example/epg.xml.gz"></div>
<div class="panel" id="xtream"><label>Server</label><input name="server" placeholder="http://server.example:8080">
<label>Username</label><input name="username" autocapitalize="off" autocomplete="off"><label>Password</label><input name="password" type="password"></div>
<div class="panel" id="file"><label>M3U file</label><input id="file-in" type="file" accept=".m3u,.m3u8,.txt">
<label>Guide URL (XMLTV, optional)</label><input name="file_epg" type="url"></div>
<label>Name</label><input name="name" placeholder="My TV">
<button class="go" type="submit">Send to TV</button><div id="msg"></div></form>
<script>
let kind='m3u';
document.querySelectorAll('.tabs button').forEach(b=>b.onclick=e=>{e.preventDefault();kind=b.dataset.p;
document.querySelectorAll('.tabs button').forEach(x=>x.classList.toggle('on',x===b));
document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('on',p.id===kind));});
document.getElementById('f').onsubmit=async e=>{e.preventDefault();const d=new FormData(e.target);const q=new URLSearchParams();
q.set('type',kind);for(const [k,v] of d.entries()){if(typeof v==='string')q.set(k,v)}
if(kind==='file'){const f=document.getElementById('file-in').files[0];if(!f){alert('Choose a file');return}q.set('content',await f.text());q.set('filename',f.name);q.set('epg',d.get('file_epg')||'')}
const r=await fetch(location.pathname,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:q.toString()});
document.getElementById('msg').textContent=r.ok?'Sent! Check your TV.':'Something went wrong.';};
</script></main></body></html>
"""
    }
}
