# Power Scheduler

Schedule your rooted Android phone to power off daily and power on before your morning alarm using hardware RTC wake alarms ( Redmi Note 10 / `mojito`).

## Requirements

- Rooted device (`su` access)
- Android 8.0+
- A kernel that:
  - exposes `/dev/rtc0`
  - supports `RTC_WKALM_SET`
  - is built with the following configs:

```plaintext
CONFIG_RTC_INTF_SYSFS=y
CONFIG_RTC_INTF_DEV=y
CONFIG_RTC_DRV_QPNP=y
CONFIG_RTC_DRV_SPMI=y
```

## How It Works

The app works relative to the RTC's clock using current time deltas:

1. Computes `delta = wake time - now` using the system clock.
2. A small bundled C binary (`rtc_setter`) reads the RTC's current time (`RTC_RD_TIME`), adds the delta, and writes it back via `RTC_WKALM_SET`.
3. Shutdown is triggered by an exact `AlarmManager` alarm (`setExactAndAllowWhileIdle`), which runs `reboot -p` as root.

## Building the NDK Binary

Compile `rtc_setter.c` for arm64 using the NDK:

```bash
aarch64-linux-android21-clang -O2 -Wl,-z,max-page-size=16384 -o librtc_setter.so rtc_setter.c
```

> The `-Wl,-z,max-page-size=16384` flag ensures 16 KB page size compatibility.

Then, in `app/build.gradle.kts`, bundle the library and configure the build types:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildTypes {
        debug {
            isDebuggable = false
        }
    }
}
```
