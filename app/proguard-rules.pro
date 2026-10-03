# kotlinx.serialization and Room ship their own consumer rules.

# PdfBox-Android references BouncyCastle (encrypted PDFs) and a JPEG 2000 decoder, which Clasp leaves out:
# Google Takeout PDFs need neither, and TakeoutPdfReader turns the resulting LinkageError into "unreadable".
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
