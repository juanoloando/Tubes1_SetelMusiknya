# Tubes1_SetelMusiknya

## Deskripsi Program

Repositori ini berisi implementasi bot kecerdasan buatan (AI) untuk turnamen **Battlecode 2025: Chromatic Conflict** yang diselenggarakan oleh MIT. Proyek ini dikembangkan untuk memenuhi Tugas Besar 1 mata kuliah **IF2211 Strategi Algoritma** dengan fokus utama penerapan **Algoritma Greedy**.

Tujuan utama dari permainan ini adalah mewarnai 70% dari total area peta yang dapat dicat, atau menghancurkan seluruh menara dan robot musuh.

Di dalam repositori ini, terdapat **tiga alternatif bot** yang masing-masing mengimplementasikan pendekatan heuristik *Greedy* yang berbeda:

1. **`mainbot` (Eksplorasi Agresif Tanpa Refill)** *[Bot Terpilih]*
* Menerapkan strategi *blitzkrieg*. Robot difokuskan murni untuk ekspansi wilayah (pengecatan) dengan cepat.
* **Heuristik:** Mengabaikan mekanisme *refill* (isi ulang cat) demi pergerakan maju yang konstan. Robot yang kehabisan cat akan otomatis menjadi agen *scout* bunuh diri (menjauh dari markas) untuk memperluas *fog of war*.


2. **`altbot1` (Macro-Efficiency & Proactive Map Control)**
* Menerapkan strategi keseimbangan dan pertahanan jangka panjang (*sustain*).
* **Heuristik:** Menggunakan navigasi aman, komunikasi *broadcasting* canggih antar unit, mekanisme *Anti-Clumping* (penghindar dempet), dan secara berkala kembali ke menara untuk melakukan *refill* cat.


3. **`altbot2` (Symmetry Exploitation & Early Rush)**
* Menerapkan strategi efisiensi informasi berdasarkan geometri peta.
* **Heuristik:** Mengasumsikan dan menebak simetri peta sejak awal permainan (Rotasi, Refleksi X, atau Refleksi Y). Mengirim gelombang *Soldier* secara masif di ronde-ronde awal untuk merebut *ruins* (reruntuhan) dan langsung menyerbu tebakan lokasi markas musuh.



---

## Requirements

Untuk menjalankan dan melakukan kompilasi program ini, pastikan sistem Anda sudah terpasang:

1. **Java Development Kit (JDK) 21 atau lebih tinggi**
* Verifikasi instalasi: `java -version`


2. **Koneksi Internet**
* Diperlukan saat pertama kali proses *build* untuk mengunduh dependensi Gradle dari *scaffold* resmi Battlecode.



> **Catatan Platform:**
> Untuk pengguna Windows, gunakan `gradlew.bat`. Untuk pengguna Linux/macOS, gunakan `./gradlew`.

---

## Cara Kompilasi

Proyek ini menggunakan **Gradle Wrapper** bawaan Battlecode. Buka terminal atau command prompt di direktori *root* proyek ini, lalu jalankan perintah berikut untuk mengkompilasi kode bot:

**Windows:**

```cmd
gradlew build

```

**Linux / macOS:**

```bash
./gradlew build

```

Perintah ini akan mengkompilasi kode Java di dalam folder `src/` dan memastikan tidak ada *error* sintaksis.

---

## Cara Menjalankan Pertandingan (Match)

Untuk menjalankan pertandingan antara bot yang ada di dalam repositori ini melawan *bot* lain (misalnya melawan `examplefuncsplayer` atau antar bot buatan sendiri), jalankan perintah:

**Windows:**

```cmd
gradlew run -PteamA=mainbot -PteamB=altbot1 -Pmaps=DefaultSmall

```

**Linux / macOS:**

```bash
./gradlew run -PteamA=mainbot -PteamB=altbot1 -Pmaps=DefaultSmall

```

**GUI:**

```cmd
Start-Process "./client/Stima Battle Client.exe"

```

**Parameter:**

* `-PteamA` : Nama folder bot tim A (contoh: `mainbot`, `altbot1`, `juans`).
* `-PteamB` : Nama folder bot tim B.
* `-Pmaps` : Nama peta yang akan dimainkan (contoh: `DefaultSmall`, `DefaultHuge`, `DonkeyKong`). Jika tidak diisi, akan menjalankan semua peta secara acak.

File hasil pertandingan (`.bc25`) akan tersimpan di dalam folder `matches/` dan dapat diputar ulang melalui aplikasi *Client* resmi Battlecode 2025.

---

## Struktur Direktori

```text
battlecode2025/
├── src/
│   ├── mainbot/               # Kode sumber untuk Bot Utama (Aggressive No-Refill)
│   │   ├── BaseRobot.java
│   │   ├── RobotPlayer.java
│   │   └── ... (Soldier, Splasher, Mopper, Tower Unit)
│   ├── altbot1/               # Kode sumber untuk Alternatif 1 (Macro-Control)
│   │   └── ...
│   └── altbot2                 # Kode sumber untuk Alternatif 2 (Symmetry Exploitation)
│       └── ...
├── matches/                   # Folder hasil keluaran pertandingan (.bc25)
├── build.gradle               # Konfigurasi Gradle Battlecode
├── gradlew                    # Script Gradle wrapper untuk Linux/Mac
├── gradlew.bat                # Script Gradle wrapper untuk Windows
└── README.md                  # File panduan ini

```

---

## Teknologi yang Digunakan

* **Bahasa Pemrograman**: Java 21+
* **Build Tool**: Gradle (Battlecode Scaffold)
* **Algoritma Utama**: Greedy

---

## Author
**Juan Oloando Simanungkalit** NIM: 13524032 <br>
**Edward David Rumahorbo** NIM: 13524036 <br>
**Reinsen Silitonga** NIM: 13524093

Program Studi: Teknik Informatika - Institut Teknologi Bandung

**Tugas Besar 1 - IF2211 Strategi Algoritma** *Semester Genap Tahun 2025/2026*

## Catatan Penting terkait Laporan
Saat pengumpulan tugas kemarin, .pdf lupa dihapus dari gitignore, sehingga laporan tidak nampak pada repository. Commit barusan dimaksudkan untuk memperbaiki hal tersebut. Tidak ada logika kode yang kami ubah.
---
