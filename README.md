# ScanStack

Android document scanner and PDF toolkit.

## What it does (version 1.0)
- **Scan** documents with the camera: automatic edge detection, cropping, filters, multi-page (Google ML Kit, on-device)
- **Import** PDFs from the phone, or open PDFs from other apps
- **Photos to PDF**
- **Merge** PDFs in any order
- **Compress** PDFs (light / medium / strong)
- **Extract pages** into a new PDF
- **Add or remove a password**
- **Get text** from documents (reads real text, or scanned pages with on-device OCR)
- **Share** or **save** to the phone

Everything runs on the phone. No account, no internet needed (except the first scanner download from Google Play services).

## How it's built
- Kotlin + Jetpack Compose (Material 3), minimum Android 8.0
- Google ML Kit Document Scanner and Text Recognition
- PdfBox-Android for PDF editing

GitHub Actions builds the app on every push to `main`
(see `.github/workflows/build.yml`). The newest APK is published under
**Releases → Latest test build**.
