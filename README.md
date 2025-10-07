# alfred.2025

**!!IMPORTANT NOTE!!**: This project is just a mostly AI generated prototype scratch project.  
Its purpose is to give me fresh ideas to either port over to Alfred2017,
or possibly make this project eventually better than Alfred2017.

## TODO

**NOT PRIORITIZED**
1. Move hard coded strings to resource files
1. Add Dark/Light/System theme mode
1. ...

## BUGS

... 

## Done
* Use version catalogs for dependencies
  https://github.com/swooby/alfred.2025/pull/8
* Update to latest gradle v9.1.0
* Add title bar to top of app
  https://github.com/swooby/alfred.2025/pull/7
* `SpeakerImpl` does not appear to be initializing correctly;
  `onInit` is never called and `ready` is always false.  
  https://github.com/swooby/alfred.2025/pull/6
* If all essential permissions (`val essentialsOk = notificationPermissionGranted && listenerGranted`) are granted
  then don't show `PermissionsScreen`, go straight to [for now] `SettingsScreen`:  
  https://github.com/swooby/alfred.2025/pull/4

## Testing

- Verify Quit flow
  1. Launch the app and confirm notification ingestion and media speech are working.
  2. Open the navigation drawer, tap “Quit”, and ensure the `com.swooby.alfred2025` process exits without restarting.
  3. Relaunch the app; verify notifications and media speech still function.
  4. Trigger “Quit” from the foreground notification; confirm the process exits and stays down.
  5. Launch the app again and confirm notification ingestion and media speech resume.
