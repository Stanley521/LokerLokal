# LokerLokal APK - Installation & Testing Guide

## 📦 APK Ready for Testing

**File**: `app/build/outputs/apk/debug/app-debug.apk`  
**Size**: ~9.2 MB  
**Build Date**: May 11, 2026  
**Version**: Debug Build  

---

## 🚀 Installation Methods

### Method 1: Using Android Studio (Recommended)

1. Open Android Studio
2. Navigate to **Tools** → **AVD Manager** (or connect a physical device)
3. Right-click the emulator and select **Cold Boot Now** (or ensure device is connected)
4. Run: **Run** → **Run 'app'** (or press **Shift + F10**)
5. Android Studio will automatically install and launch the app

### Method 2: Using ADB (Android Debug Bridge)

```bash
# For emulator or connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or, if you have multiple devices
adb devices  # List connected devices
adb -s <device_id> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 3: Direct APK File Transfer

1. Connect your device via USB
2. Enable Developer Mode & USB Debugging on the device
3. Copy the APK file to your device
4. Open a file manager on the device
5. Navigate to the APK and tap to install

---

## 📋 Pre-Installation Checklist

- [ ] Device has Android 7.0+ (API level 24+)
- [ ] Device has Google Play Services installed
- [ ] Location permissions are available
- [ ] Internet connection is active
- [ ] Maps API key is configured (in local.properties)

---

## 🧪 Testing Checklist

### Core Functionality
- [ ] App installs without errors
- [ ] App launches without crashing
- [ ] Map displays correctly
- [ ] Location tracking works
- [ ] Job markers appear on map
- [ ] Bottom sheets are draggable

### Map Features
- [ ] Tapping marker opens PlaceBottomSheet
- [ ] Tapping job card opens JobApplyBottomSheet
- [ ] Zoom buttons work
- [ ] Current location button works
- [ ] Map pans smoothly

### Job Lists
- [ ] Jobs display in cards (horizontally scrollable)
- [ ] Job images load from Google Places API
- [ ] Distance is calculated correctly
- [ ] Pay text displays correctly

### Bottom Sheets
- [ ] PlaceBottomSheet shows "Loker Lokal" for nearby jobs
- [ ] PlaceBottomSheet shows place name for marker tap
- [ ] JobApplyBottomSheet shows full job details
- [ ] Close button works on apply sheet
- [ ] Back button works on apply sheet

### Theme & UI
- [ ] Colors match defined palette (#00CED1 primary)
- [ ] Text is readable on all backgrounds
- [ ] Buttons are properly styled
- [ ] Spacing and padding are consistent
- [ ] UI is not oversized on device

### Resume Feature
- [ ] PDF upload button appears
- [ ] PDF upload works (max 500KB)
- [ ] Resume status displays correctly
- [ ] WhatsApp button works

---

## 🔧 Troubleshooting

### Installation Issues

**"Could not find gradle"**
```bash
cd /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal
./gradlew assembleDebug
```

**"INSTALL_FAILED_INVALID_APK"**
- Rebuild: `./gradlew clean assembleDebug`

**"INSTALL_FAILED_INSUFFICIENT_STORAGE"**
- Device needs 50+ MB free space

### Runtime Issues

**App crashes on startup**
```bash
adb logcat | grep lokerlokal
```
Check for:
- Missing API keys in local.properties
- Supabase connection issues
- Missing permissions

**Map not loading**
- Verify Google Maps API key in local.properties
- Check location permissions are granted
- Ensure internet connection is active

**Job data not loading**
- Check Supabase connection
- Verify internet connectivity
- Check logcat for RPC errors

---

## 📊 APK Specifications

```
APK Name: app-debug.apk
Size: ~9.2 MB
Min SDK: 24 (Android 7.0)
Target SDK: 34 (Android 14)
Package: com.example.lokerlokal
Build Type: Debug
Signature: Debug keystore
```

---

## ⚠️ Important Notes

- ✅ This is a **Debug APK** for testing only
- ❌ Do **NOT** use for production or release
- 🔒 Supabase Edge Function has `verify_jwt=false` (testing only)
- 🔐 For production, you need a **Release APK** with proper signing

---

## 📞 Support

For issues:
- Check the logcat output for error messages
- Review README.md for project documentation
- Check JWT_SECURITY_REMINDER.md for security concerns
- Verify Supabase configuration

---

**Status**: ✅ Ready to Install  
**Last Built**: May 11, 2026  


