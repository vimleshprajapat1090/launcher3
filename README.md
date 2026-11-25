# Island Home — prototype 0.1
An original minimal Android launcher and notification-island prototype. Not an Apple product and not a complete iOS clone.

## Build using a tablet
1. Extract this ZIP. Create an empty GitHub repository in your browser.
2. Upload the CONTENTS of this directory to the repository root, not the enclosing directory. Enable browser Desktop site if necessary. Include the hidden .github directory; the workflow must be at .github/workflows/build.yml. If your file picker hides it, create that exact file using GitHub Add file > Create new file and paste the supplied workflow contents.
3. Open Actions > Build APK > Run workflow. If Actions needs enabling, enable it first. This uses GitHub-hosted build resources; check your account quota/billing, particularly for private repositories.
4. After a successful run, download the IslandHome-debug-apk artifact. Extract its ZIP and install the APK. Android may request permission to install unknown apps for your browser/file manager. Only install your own verified build.
5. Open Island Home > Setup. Set default home app, grant overlay access, optionally notification access and notification permission. Press Island on.
6. Tap the pill to expand/collapse; long-press to open the latest notification when it has a valid content intent. Off stops the overlay.

## What is actually implemented
- Responsive 4–8 column installed-app grid, live app search, gradient background, rounded icon clipping, fixed Phone/Web/Camera dock.
- Foreground-service overlay above ordinary app windows after explicit permission.
- Latest notification title/text, expandable pill, notification content-intent action.
- Manual stop control. No automatic restart or boot launch.

## Not implemented / known limitations
- No Apple icon pack, authentic iOS animations, folders, home-page editing, app library categories, widgets, wallpaper picker, Control Center, lock screen, music playback controls or notification queue.
- Existing Android app icons are used; rounded clipping does not turn them into Apple icons.
- Overlay is below critical system windows; apps may block overlays. Camera-cutout alignment is not calibrated. On some devices the pill starts below the status bar.
- Tablet and phone compatibility must be tested. Orientation changes reset the activity. App list refreshes when activity is recreated. Some work-profile/private-space apps are outside this prototype.
- Notification removal currently clears the display even if a different notification was showing.
- Notification access can expose sensitive notification content. Only grant it knowingly. No Internet permission is requested by this app; tapping Web launches your browser.
- Foreground overlay availability and background service survival depend on Android version and manufacturer restrictions. No promise of all-app/lockscreen visibility.
- This source has NOT been compiled or device-tested in the generation environment. The first GitHub Actions run is the build verification. Debug APK is for testing, not a production release or Play Store-ready submission.
- Keep another launcher installed. To revert, open Android Settings > Apps > Default apps > Home app and select the previous launcher. Disable overlay/notification access and uninstall if desired.

## Build versions
Java 17, Gradle 8.9 supplied by Actions, Android Gradle Plugin 8.7.3, compile/target SDK 35, minimum SDK 26. No Gradle wrapper binary is included; Actions installs Gradle explicitly. Network access on the GitHub runner is needed for dependencies.
