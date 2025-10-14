# alfred.2025

**!!IMPORTANT NOTE!!**: This project is just a mostly AI generated prototype scratch project.  
Its purpose is to give me fresh ideas to either port over to Alfred2017,
or possibly make this project eventually better than Alfred2017.

## TODO

NOTE: Regularly check Keep notes and move to here.

**NOT PRIORITIZED**
1. Lower volume for X minutes/hours (aka: Attenuate)
1. Do Not Disturb for X minutes/hours (aka: Snooze)
1. Detect and honor "Do Not Disturb" hours
1. Notification Mute, Snooze, Stop
1. Notification Media Controls
1. Friendly don't say "Greater Than" for ">" prefixed quoted lines
1. Friendly don't say "Underscore" for "FOO_BAR"
1. "Smart Switch" (or any similar) Notification of "Backing Up" progress causes annoying repeats
1. Dictionary to change way words are pronounced; ex: Friendlier name for LOCKLY app
1. Detect when phone in pocket and Always On and go to 100% volume https://www.google.com/search?q=Android+detect+if+phone+is+in+pocket
1. Detect when removed from pocket and restore to previous volume
1. "Lock after screen timeout" is a useful device setting to detect screen locked
1. Event commands:
   * Copy to clipboard
   * Share
   * Speak
   * ...
1. Gmail notification needs better reading.
1. "Android System: Data warning - You've used 2.38 GB of data" is repeating a lot...basically not deduping at all!
   What is very interesting (clue?) is that peer "Wireless debugging connected" is not repeating.
1. Coalesce similar duplicate Media and Notifications
1. TTS audio needs to route to selected audio device
1. Future option to show Media Playing info directly in app as if it was actually playing in the app, perhaps with even Pause, Stop, Next, Like, Favorite, Playlist, etc.
1. Future option to show history on map similar to Google Maps
1. Future option to show tasks similar to Google Keep or Google Tasks
1. Future option to show list view of events as a calendar similar to Google Calendar
1. Improve EventCard; simplify for non-Dev user
1. Improve media playing logic to better announce song stop/start transitions.
1. Add detailed logging of event parsing result.  
   This might result in noticing that the parsing is not as thorough as in Alfred 2017.
1. Add exhaustive notification parsing information to EventCard.
1. When starting up:
    1. Say greeting
    1. Start hourly summary including read all current notifications
1. Add user's name, gender, Alfred voice, pitch, formality, etc to Settings.
1. Setting to announce time every whole, half, or quarter hour.
1. Special debug UI to control debug settings:
    1. Toggle VERBOSE_LOG_SPEECH
    1. Toggle VERBOSE_LOG_UTTERANCE_IDS
    1. Toggle VERBOSE_LOG_UTTERANCE_PROGRESS
    1. Toggle VERBOSE_LOG_AUDIO_FOCUS
    1. Toggle showing mUtteranceCallbacks contents (to help debug anything that may look like a "leak")
1. Consider elevating the code to a first class Accessibility Service and so what possibilities that opens up.
1. Understand how this app can launch to listen when app is in background, but Alfred.2017 can't
1. Screen on total time (for day?) announcement
1. Add "Screen on, wes off for XX minutes" and "Screen off, was on for XX minutes"
   Repeat for: 
   * charging
   * network
   * ...
1. Speak time durations/intervals in more human understandable expanded "1 hour, 27 minutes, 14 seconds"
1. ...
1. YouGPT: Summarize Alfred data in a GPT config file
1. ...

## BUGS

1. HourlyDigestWorker seems to be announcing every hour from the app start time;
   it might make more sense to have HourlyDigestWorker announce at the top of the hour.
   The app will soon announce things when it starts; if the app starts less than 5 minutes
   to the top of the hour and already announces things then it might make sense to not announce
   again so soon at the top of the hour; maybe just summarize things that have changed since
   the last announcement... but that is probably the way top of the hour announcements should
   probably work anyway.
... 

## Done
* Add notification PendingIntent that launches app.
* Audio profiles now gate speech output (Always Off / headset-only modes)
  https://github.com/swooby/alfred.2025/pull/26
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
