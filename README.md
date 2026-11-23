# Dit's Tunnel (v0.1)

Klien SSH tunnel untuk Android. Versi dasar: login SSH (host, port, user, password),
lalu membuka proxy SOCKS5 lokal di `127.0.0.1:1080` yang diteruskan lewat server SSH.

## Build tanpa PC
1. Buat repo baru di GitHub, upload seluruh isi folder ini.
2. Buka tab **Actions** → workflow **Build APK** → **Run workflow**
   (atau otomatis jalan saat push ke `main`).
3. Setelah selesai, download artifact **DitsTunnel-debug-apk**, ekstrak, install `app-debug.apk`.

## Cara pakai
1. Isi host, port, username, password server SSH → **CONNECT**.
2. Arahkan aplikasi/browser ke proxy SOCKS5 `127.0.0.1:1080`
   (mis. Firefox: Settings → Network → SOCKS5 host 127.0.0.1 port 1080, centang proxy DNS).

## Belum ada (rencana)
- Mode VPN seluruh perangkat (VpnService + tun2socks)
- SSL/TLS & payload/SNI
- Simpan profil, key-based auth
