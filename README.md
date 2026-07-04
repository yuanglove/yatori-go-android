# Yatori Android

Android wrapper for Yatori. This repository hosts Android releases, update metadata, and the remote announcement file used by the app.

## Remote Metadata

- Announcement: `https://raw.githubusercontent.com/yuanglove/yatori-go-android/main/announcement.json`
- Releases: `https://github.com/yuanglove/yatori-go-android/releases`
- Latest release API: `https://api.github.com/repos/yuanglove/yatori-go-android/releases/latest`

## Notes

The generated `app/libs/mobileapi.aar` is intentionally not committed because it is larger than GitHub's normal file limit. Build scripts should regenerate it from the Go mobile API, and installable APKs should be uploaded as GitHub Release assets.
