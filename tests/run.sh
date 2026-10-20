#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
java_command="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -d "$test_dir" "$project_dir/app/src/main/java/com/simon/offlinebms/JbdProtocol.java" "$project_dir/app/src/main/java/com/simon/offlinebms/JbdAuthentication.java" "$project_dir/app/src/main/java/com/simon/offlinebms/JkProtocol.java" "$project_dir/app/src/main/java/com/simon/offlinebms/VictronProtocol.java" "$project_dir/app/src/main/java/com/simon/offlinebms/EnergyStats.java" "$project_dir/app/src/main/java/com/simon/offlinebms/AlertEngine.java" "$project_dir/tests/ProtocolTests.java" "$project_dir/tests/AuthenticationTests.java" "$project_dir/tests/FeatureTests.java" "$project_dir/app/src/main/java/com/simon/offlinebms/JbdSettings.java" "$project_dir/app/src/main/java/com/simon/offlinebms/JbdSettingsSession.java" "$project_dir/tests/SettingsTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.ProtocolTests
"$java_command" -cp "$test_dir" com.simon.offlinebms.AuthenticationTests
"$java_command" -cp "$test_dir" com.simon.offlinebms.FeatureTests "$project_dir/tests/fixtures"

"$java_command" -cp "$test_dir" com.simon.offlinebms.SettingsTests
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -cp "$test_dir" -d "$test_dir" "$project_dir/tests/VictronChargerTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.VictronChargerTests "$project_dir/tests/fixtures"
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -cp "$test_dir" -d "$test_dir" "$project_dir/tests/VictronFormatsTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.VictronFormatsTests "$project_dir/tests/fixtures"
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -cp "$test_dir" -d "$test_dir" "$project_dir/tests/JkDiagnosticsTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.JkDiagnosticsTests
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -cp "$test_dir" -d "$test_dir" "$project_dir/tests/JkCapturedTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.JkCapturedTests "$project_dir/tests/fixtures"
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 11 -cp "$test_dir" -d "$test_dir" "$project_dir/tests/BalancingTests.java"
"$java_command" -cp "$test_dir" com.simon.offlinebms.BalancingTests
