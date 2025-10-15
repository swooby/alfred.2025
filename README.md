# alfred.2025

**!!IMPORTANT NOTE!!**: This project is just a mostly AI generated prototype scratch project.  
Its purpose is to give me fresh ideas to either port over to Alfred2017,
or possibly make this project eventually better than Alfred2017.

## TODO

NOTE: Regularly check Keep notes and move to here.

### Notification & Audio Controls
1. Detect and honor system Do Not Disturb schedules before speaking or playing announcements.
1. Implement timed notification controls (mute, snooze, temporary volume attenuation) so users can pause Alfred for set durations.
    * Lower volume for X minutes/hours (aka: Attenuate)
    * Do Not Disturb for X minutes/hours (aka: Snooze)
    * Pick and choose what and what not to snooze?
1. Expose notification media playback controls (play, pause, next, stop) from Alfred's UI or speech layer.
1. Route TTS audio to the user-selected audio device (speaker, headset, car) to respect user context.
1. Refine media playback announcements to better indicate song start/stop transitions without redundant chatter.

### Device Context Automation
1. Use the "Lock after screen timeout" system setting to detect a reliable screen-locked state for pipeline decisions.
1. Automate pocket-based volume management: detect when the phone is pocketed to temporarily boost announcements, then restore the previous volume once removed.

### Speech Output Polish
1. Normalize symbol-heavy snippets so ">" prefixed quotes and identifiers like "FOO_BAR" are spoken naturally.
    * Don't say "Greater Than" for ">" prefixed quoted lines
    * Don't say "Underscore" for "FOO_BAR"
1. Maintain a pronunciation dictionary for app names and custom terms (e.g., friendlier wording for LOCKLY).
1. Improve Gmail notification summaries to emphasize sender, subject, and actionable content.
1. Format spoken durations and intervals in natural language (e.g., "1 hour, 27 minutes, 14 seconds").
1. On startup, greet the user and kick off the hourly summary (including current notifications) once permissions allow.

### Event Processing & Diagnostics
1. Add detailed logging of event parsing results to surface ingestion gaps not as thorough as Alfred 2017.
1. Surface exhaustive notification parsing information inside `EventCard` for easier troubleshooting.
1. Improve coalescing logic for similar media and notification events to reduce repeated speech.
1. Stop the "Smart Switch" backup notification from generating repeated "Backing Up" announcements.
1. Fix the "Android System: Data warning" notification so it dedupes instead of repeating very frequently.

### Announcements & Timekeeping
1. Announce screen, charging, and network session durations along with daily totals
    * "Screen on. Was off for 45 minutes."/"Screen off. Was on for 3 minutes."
    * Charging
    * Cellular
    * WiFi (Network Name)
1. Offer a setting to announce the time on whole-, half-, or quarter-hour intervals.
    * Screen on total time (for day?)
    * ...

### UI & Interaction Enhancements
1. Simplify the `EventCard` for non-developer users while keeping key diagnostic details accessible.
1. Provide quick event actions (copy to clipboard, share, replay speech, ...) from cards or voice commands.
1. Investigate displaying currently playing media details directly in-app, with optional Pause, Stop, Next, Like, Favorite, Playlist, etc. controls.
1. Add richer event history views (Map-based, Keep/Task-like lists, and Calendar timelines) for exploring stored events.

### Personalization & Summaries
1. Expand Settings with user identity and voice profile options (name, gender, Alfred voice, pitch, formality, etc).
1. Generate a YouGPT configuration that summarizes Alfred data for downstream GPT workflows.

### Developer Tooling & Platform Exploration
1. Build a dedicated debug UI to toggle verbose logging flags (speech, utterance IDs/progress, audio focus, utterance callbacks).
    1. Toggle VERBOSE_LOG_SPEECH
    1. Toggle VERBOSE_LOG_UTTERANCE_IDS
    1. Toggle VERBOSE_LOG_UTTERANCE_PROGRESS
    1. Toggle VERBOSE_LOG_AUDIO_FOCUS
    1. Toggle showing mUtteranceCallbacks contents (to help debug anything that may look like a "leak")
1. Learn how this app can start foreground notification when in background but Alfred2017 can't (crashes if not in foreground).
1. Evaluate promoting the app to a full Accessibility Service and document the new capabilities it would unlock.

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
