# LokerLokal - Quick Reference & File Locations

## 📍 Important File Locations

### APK (Ready to Install)
```
📦 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── app/build/outputs/apk/debug/
       └── app-debug.apk  ← INSTALL THIS FILE (8.0 MB)
```

### Documentation Files (in project root)
```
📄 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   ├── README.md                      ← START HERE
   ├── CONTINUATION_CHECKLIST.md      ← Status overview
   ├── THEME_IMPLEMENTATION.md        ← Color reference
   ├── APK_INSTALLATION_GUIDE.md      ← Installation steps
   └── JWT_SECURITY_REMINDER.md       ← Security checklist
```

### Theme Configuration Files
```
🎨 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── app/src/main/res/
       ├── values/
       │   ├── colors.xml             ← Light mode (25 colors)
       │   └── themes.xml             ← Light mode theme
       └── values-night/
           ├── colors.xml             ← Dark mode (17 colors)
           └── themes.xml             ← Dark mode theme
```

### Layout Files (with theme colors)
```
📱 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── app/src/main/res/layout/
       ├── fragment_home.xml          ← Local jobs list
       ├── fragment_notifications.xml ← Notifications
       ├── fragment_dashboard.xml     ← Resume management
       ├── fragment_map.xml           ← Main map screen
       ├── bottom_sheet_job_apply.xml ← Job details & apply
       ├── bottom_sheet_place_details.xml ← Place list
       ├── item_local_job_card.xml    ← Job card item
       ├── item_business_photo.xml    ← Photo item
       └── item_notification_job.xml  ← Notification item
```

### Drawable Files (with theme colors)
```
🎨 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── app/src/main/res/drawable/
       ├── bg_local_job_distance_pill.xml
       ├── bg_sheet_handle_chip.xml
       ├── drag_handle.xml
       ├── bg_bottom_sheet_top_rounded.xml
       ├── bg_map_control_button.xml
       ├── bg_info_grid_rounded_top.xml
       ├── bg_job_type_chip.xml
       ├── bg_primary_gradient.xml
       └── bg_image_loading.xml
```

### Kotlin Source Files
```
🔧 /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── app/src/main/java/com/example/lokerlokal/
       ├── ui/map/
       │   ├── MapFragment.kt
       │   ├── MapJobsSharedViewModel.kt
       │   └── JobApplyBottomSheetFragment.kt
       ├── ui/home/LocalFragment.kt
       ├── ui/dashboard/DashboardFragment.kt
       ├── ui/notifications/NotificationsFragment.kt
       └── data/remote/
           ├── SupabaseJobsService.kt
           └── SupabaseResumeService.kt
```

### Backend (Supabase)
```
🗄️ /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
   └── supabase/
       ├── config.toml               ← Edge function config
       ├── places_jobs_schema.sql    ← Database schema
       ├── resume_schema.sql         ← Resume table
       └── functions/google-place-details/
           └── ...                   ← Place API handler
```

---

## 🎯 Which File Do I Need?

### I want to...

**...understand the project status**
→ Read: `README.md` or `CONTINUATION_CHECKLIST.md`

**...install the app on a device**
→ Follow: `APK_INSTALLATION_GUIDE.md`
→ Use: `app-debug.apk`

**...understand the color system**
→ Read: `THEME_IMPLEMENTATION.md`
→ Edit: `values/colors.xml` or `values-night/colors.xml`

**...modify a UI layout**
→ Edit: `app/src/main/res/layout/*.xml`

**...change colors in a drawable**
→ Edit: `app/src/main/res/drawable/*.xml`

**...check before production**
→ Read: `JWT_SECURITY_REMINDER.md`

**...view build settings**
→ Edit: `build.gradle.kts` or `app/build.gradle.kts`

---

## 🔑 Color Palette Reference

### Primary Colors
```
Primary Main:   #00CED1 ← Use for CTAs and primary elements
Primary Dark:   #00A3A6 ← Use for hover/active states
Primary Light:  #E6FAFA ← Use for light backgrounds
```

### Text Colors
```
Text Primary:   #0F172A ← Main text
Text Secondary: #475569 ← Secondary text
```

### Status Colors
```
Success: #22C55E ✅
Warning: #F59E0B ⚠️
Error:   #EF4444 ❌
Info:    #38BDF8 ℹ️
```

### Other
```
Background:    #F8FAFC ← Main background
Divider:       #CBD5E1 ← Borders, separators
Warm Accent:   #FF9F1C ← CTA highlights
Soft Accent:   #FFE8CC ← Subtle accents
```

### Dark Mode (values-night/)
```
All colors adjusted for dark backgrounds
Better contrast for text readability
Desaturated primaries for dark mode
```

---

## ⚡ Quick Commands

### Install APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build New APK
```bash
cd /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal
./gradlew assembleDebug
```

### View Logs
```bash
adb logcat | grep lokerlokal
```

### Rebuild (Clean)
```bash
./gradlew clean assembleDebug
```

---

## 📊 Build Information

```
Project: LokerLokal
Location: /Users/stanleywong2197gmail.com/AndroidStudioProjects/LokerLokal/
Gradle: 8.11.1
Kotlin: 2.0.20
Min SDK: 24 (Android 7.0)
Target SDK: 34 (Android 14)

Latest Build: ✅ SUCCESS
APK Size: 8.0 MB
Last Built: May 11, 2026
```

---

## 🚀 Getting Started

### Step 1: Install APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Test Features
- Open Maps
- Tap markers
- Scroll job cards
- Test bottom sheets
- Verify colors

### Step 3: Report Issues
- Check logcat for errors
- Note device & Android version
- Document steps to reproduce

### Step 4: Before Production
- Read JWT_SECURITY_REMINDER.md
- Enable JWT verification
- Build release APK
- Test thoroughly

---

## 📞 Getting Help

### Logcat Output Location
- **Terminal**: `adb logcat`
- **Android Studio**: Logcat window (bottom)
- **Filter**: `adb logcat | grep lokerlokal`

### Documentation Location
All docs in project root:
- README.md
- THEME_IMPLEMENTATION.md
- CONTINUATION_CHECKLIST.md
- APK_INSTALLATION_GUIDE.md
- JWT_SECURITY_REMINDER.md

### Error Messages
Check in this order:
1. Logcat output for stack trace
2. APK_INSTALLATION_GUIDE.md for common issues
3. README.md for overall guidance
4. JWT_SECURITY_REMINDER.md for security issues

---

## ✅ Checklist for First Time

- [ ] Read README.md
- [ ] Install APK using adb install command
- [ ] Open Maps tab first
- [ ] Wait for location to load
- [ ] Verify colors look right
- [ ] Test tapping a marker
- [ ] Test opening job card
- [ ] Test bottom sheet dragging
- [ ] Check logcat for any errors

---

**Status**: ✅ Ready to Deploy
**Date**: May 11, 2026
**Version**: Debug Build


