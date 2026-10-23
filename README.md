# ScanStack

Android document scanner and PDF toolkit.

## What it does (version 1.5)
- **Scan** documents with the camera: automatic edge detection, cropping, filters, multi-page (Google ML Kit, on-device)
- **Import** PDFs from the phone, or open PDFs from other apps
- **Photos to PDF**
- **Fill & Sign**: draw, type (5 signature fonts) or upload a signature; add text in 5 fonts with bold/italic/underline, dates, ticks and crosses anywhere on a page
- **Watermark**: typed text or an uploaded logo, diagonal / center / tiled / corner, adjustable size and opacity
- **Add page**: scan more pages straight into an open PDF
- **Page selection**: press and hold a page to select pages, then delete them or copy them into a new PDF
- **Fill form**: type into PDF form fields (text, checkboxes, radio buttons, dropdowns), optionally lock the answers
- **Word to PDF**: convert .docx (and .txt) files on the phone
- **Merge** PDFs in any order
- **Compress** PDFs (light / medium / strong)
- **Extract pages** into a new PDF
- **Add or remove a password**
- **Get text** from documents (reads real text, or scanned pages with on-device OCR)
- **Share** or **save** to the phone

Documents are saved in **Internal storage/ScanStack** (changeable in Settings). Everything runs on the phone. No account, no internet needed (except the first scanner download from Google Play services).

## How it's built
- Kotlin + Jetpack Compose (Material 3), minimum Android 8.0
- Google ML Kit Document Scanner and Text Recognition
- PdfBox-Android for PDF editing

GitHub Actions builds the app on every push to `main`
(see `.github/workflows/build.yml`). The newest APK is published under
**Releases → Latest test build**.

## Fonts
Signature and handwriting fonts (Great Vibes, Allura, Sacramento, Mrs Saint Delafield, Dancing Script, Caveat) are from Google Fonts under the SIL Open Font License; licenses are in `app/src/main/assets/licenses`.
