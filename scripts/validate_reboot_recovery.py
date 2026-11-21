#!/usr/bin/env python3
"""Static regression checks for Shift Alarm reboot recovery wiring."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"

permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
required_permissions = {
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.SCHEDULE_EXACT_ALARM",
}
missing = required_permissions - permissions
assert not missing, f"Missing manifest permissions: {sorted(missing)}"

app = manifest.find("application")
assert app is not None
receivers = {node.get(android + "name"): node for node in app.findall("receiver")}
boot = receivers.get(".BootReceiver")
assert boot is not None, "BootReceiver is not registered"
actions = {
    action.get(android + "name")
    for intent_filter in boot.findall("intent-filter")
    for action in intent_filter.findall("action")
}
assert "android.intent.action.BOOT_COMPLETED" in actions
assert "android.intent.action.MY_PACKAGE_REPLACED" in actions
assert "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" in actions

base = root / "app/src/main/java/com/shiftalarm/app"
boot_source = (base / "BootReceiver.kt").read_text()
scheduler_source = (base / "AlarmScheduler.kt").read_text()
store_source = (base / "ShiftScheduleStore.kt").read_text()
prefs_source = (base / "AlarmPreferences.kt").read_text()
receiver_source = (base / "AlarmReceiver.kt").read_text()
gradle_source = (root / "app/build.gradle.kts").read_text()

checks = {
    "boot receiver loads saved schedules": "getAllShifts()" in boot_source,
    "boot receiver reschedules each saved shift": "scheduler.scheduleShift(date, code)" in boot_source,
    "boot receiver checks exact-alarm access": "canScheduleExactAlarms()" in boot_source,
    "scheduler uses exact alarm clock API": "alarmManager.setAlarmClock(" in scheduler_source,
    "scheduler checks exact-alarm access": "canScheduleExactAlarms()" in scheduler_source,
    "schedule store persists changes synchronously": ".commit()" in store_source,
    "active alarm state persists synchronously": ".commit()" in prefs_source and "setActiveAlarm(" in prefs_source,
    "alarm receiver starts foreground alarm service": "startForegroundService(serviceIntent)" in receiver_source,
    "existing package ID is preserved": 'applicationId = "com.shiftalarm.app2"' in gradle_source,
    "Android 11+ compatibility is preserved": "minSdk = 30" in gradle_source,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
assert not failed, "Reboot recovery static checks failed: " + "; ".join(failed)
print("PASS: reboot recovery wiring checks")
