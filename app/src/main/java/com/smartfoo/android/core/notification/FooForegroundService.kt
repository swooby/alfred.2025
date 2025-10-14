package com.smartfoo.android.core.notification

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat

@Suppress("unused")
class FooForegroundService {
    companion object {
        /**
         * Maps a single `foregroundServicePermission` string to its corresponding
         * `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` constant.
         *
         * This is a helper for [getForegroundServiceTypes].
         *
         * See also: [https://developer.android.com/develop/background-work/services/fgs/service-types](https://developer.android.com/develop/background-work/services/fgs/service-types)
         *
         * @param foregroundServicePermission A single `Manifest.permission.FOREGROUND_SERVICE_*` permission.
         * @return The corresponding `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` constant.
         * @throws IllegalArgumentException if the provided permission is unknown.
         */
        fun getForegroundServiceType(foregroundServicePermission: String): Int =
            when (foregroundServicePermission) {
                Manifest.permission.FOREGROUND_SERVICE_CAMERA -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                Manifest.permission.FOREGROUND_SERVICE_HEALTH -> ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                Manifest.permission.FOREGROUND_SERVICE_LOCATION -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL -> ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                Manifest.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                Manifest.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        if (foregroundServicePermission == Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                        } else {
                            throw IllegalArgumentException("Unknown foregroundServicePermission $foregroundServicePermission")
                        }
                    } else {
                        throw IllegalArgumentException("Unknown foregroundServicePermission $foregroundServicePermission")
                    }
                }
            }

        /**
         * Iterates over the given `foregroundServicePermissions` and returns a bitmasked `Int` of all
         * matching `FOREGROUND_SERVICE_TYPE`s.
         *
         * [android.app.Service.startForeground]`(int id, Notification notification, int foregroundServiceType)`
         * may then then be called with the returned `foregroundServiceType`.
         *
         * Optionally a `foregroundServiceType = `[ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST] may be used
         * to indicate to use all types set in manifest file.
         *
         * See also: [https://developer.android.com/develop/background-work/services/fgs/service-types](https://developer.android.com/develop/background-work/services/fgs/service-types)
         *
         * @param foregroundServicePermissions A `vararg` of `Manifest.permission.FOREGROUND_SERVICE_*`
         *   permissions.
         * @return A bitmasked `Int` combining all corresponding `ServiceInfo.FOREGROUND_SERVICE_TYPE_*`
         *   constants.
         * @throws IllegalArgumentException if any of the provided permissions are unknown.
         *
         */
        fun getForegroundServiceTypes(vararg foregroundServicePermissions: String): Int {
            var serviceType = 0
            foregroundServicePermissions.forEach { foregroundServicePermission ->
                serviceType = serviceType or getForegroundServiceType(foregroundServicePermission)
            }
            return serviceType
        }

        /**
         * From [https://developer.android.com/about/versions/14/changes/fgs-types-required#include-fgs-type-runtime](https://developer.android.com/about/versions/14/changes/fgs-types-required#include-fgs-type-runtime):
         * > The best practice for applications starting foreground services is to use
         * > [ServiceCompat.startForeground]`(...)` (available in androidx-core 1.12 and higher)]
         * > where you pass in a **bitwise integer of foreground service types**.
         * > You can choose to pass one or more type values.
         *
         * > Usually, you should declare only the types required for a particular use case.
         * > This makes it easier to meet the
         * > [system's expectations for each foreground service type](https://developer.android.com/guide/components/foreground-services#fgs-prerequisites).
         *
         * > `ServiceCompat.startForeground(0, notification, FOREGROUND_SERVICE_TYPE_LOCATION)`
         *
         * > **If the foreground service type is not specified in the call,
         * > the type defaults to the values defined in the manifest.**
         * > If you didn't specify the service type in the manifest, the system throws
         * > [MissingForegroundServiceTypeException](https://developer.android.com/reference/android/app/MissingForegroundServiceTypeException).
         *
         * > In cases where a foreground service is started with multiple types, then the
         * > foreground service must adhere to the
         * > [platform enforcement requirements](https://developer.android.com/guide/components/foreground-services#runtime-permissions)
         * > of all types.
         *
         * > If the foreground service needs new permissions after you launch it, you should call
         * > `startForeground()` again and add the new service types.
         * > For example, suppose a fitness app runs a running-tracker service that always needs
         * > `location` information, but might or might not need `media` permissions. You would need
         * > to declare both `location` and `mediaPlayback` in the manifest. If a user starts a run
         * > and just wants their location tracked, your app should call `startForeground()` and
         * > pass just the `location` service type. Then, if the user wants to start playing audio,
         * > call `startForeground()` again and pass `location|mediaPlayback`.
         */
        fun startForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
        ) {
            ServiceCompat.startForeground(service, id, notification, foregroundServiceType)
        }
    }
}
