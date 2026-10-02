# Clasp

A calm, private wallet for loyalty cards and passes on Android. No payments, no accounts, no network.

## Development

Requirements: Android Studio (for its bundled JDK and the Android SDK at `~/Android/Sdk`).

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
env JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug        # build
env JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest    # tests (JVM + Robolectric)
env JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug         # install on a connected device
env JAVA_HOME=/opt/android-studio/jbr ./gradlew verifyRoborazziDebug # compare screens with golden images
env JAVA_HOME=/opt/android-studio/jbr ./gradlew recordRoborazziDebug # re-record goldens after an intended UI change
```

## License

Copyright (C) 2026 Patrick X. Gray

Clasp is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. See [LICENSE](LICENSE).

Clasp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
