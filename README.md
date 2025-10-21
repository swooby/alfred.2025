# alfred.2025

**!!IMPORTANT NOTE!!**: This project is just a mostly AI generated prototype scratch project.  
Its purpose is to give me fresh ideas to either port over to Alfred2017,
or possibly make this project eventually better than Alfred2017.

## Runtime permissions

Alfred 2025 needs a few runtime permissions to stay useful:
- **Post Notifications** so the app can keep its persistent status card visible.
- **Notification Listener Access** to ingest notifications and media sessions.
- **Phone status (READ_PHONE_STATE)** to pause speech while you're on a call, queue announcements quietly, and resume once the call ends.

## TODO

NOTE: Regularly check Keep notes and vet into here and add to GitHub.

UNVETTED:
* Add FooTextToSpeech pause/resume functionality

## BUGS

1. HourlyDigestWorker seems to be announcing every hour from ... the app start time?  
   It might make more sense to have HourlyDigestWorker announce at the top of the hour.
   The app will soon announce things when it starts; if the app starts less than 5 minutes
   to the top of the hour and already announces things then it might make sense to not announce
   again so soon at the top of the hour; maybe just summarize things that have changed since
   the last announcement... but that is probably the way top of the hour announcements should
   probably work anyway.
1. Phone numbers may be spoken as "six billion one hundred ninety seven million nine hundred sixty six thousand two hundred ninety nine".  
    Need to speak them as single numbers "6 1 9 7 9 6 6 2 9 9".
 
... 

## Done
* FooTextToSpeech now queues requests, returns a cancelable `sequenceId`, and supports NEXT/IMMEDIATE/CLEAR placement to preempt playback.
* "Screen on. Was off for X." / "Screen off. Was on for Y."
* Add notification PendingIntent that launches app.
* Audio profiles now gate speech output (Always Off / headset-only modes)
  https://github.com/swooby/alfred.2025/pull/26
* System event ingestion rebuilt on Flows: display, boot/shutdown, and power changes now speak status automatically.  
* Properly announces Screen On/Off; more to come... and better organized.
* Get notification coalesce/dedupe/debound working (better)
  https://github.com/swooby/alfred.2025/pull/25
* Add Dark/Light/System theme mode
  https://github.com/swooby/alfred.2025/pull/24
* Have MainActivity efficiently show a list of the stored events;
  Most recent events should be at the top.
  Scrolling down will continue to load from storage.
  List should update automatically whenever a new event is stored.
  https://github.com/swooby/alfred.2025/pull/18
* AudioProfileController now stops FooTextToSpeech when the audio gate closes, preventing runaway speech.
* Get audio ducking working again
  https://github.com/swooby/alfred.2025/pull/12
* Move hard coded strings to resource files
  https://github.com/swooby/alfred.2025/pull/10
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
