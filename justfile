set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

alias b := build
alias sa := send-apk

app-name := "QuickerDynalist"
version := `awk -F'"' '/versionName/ { print $2; exit }' app/build.gradle`
output-dir := "outputs"
apk-name := app-name + "_" + version + ".apk"

build:
    ./gradlew assembleRelease
    mkdir -p "{{output-dir}}"
    cp "app/build/outputs/apk/release/app-release.apk" "{{output-dir}}/{{apk-name}}"
    @echo "{{output-dir}}/{{apk-name}}"

send-apk: build
    mkdir -p "$HOME/Syncthing/APK/{{app-name}}"
    cp "{{output-dir}}/{{apk-name}}" "$HOME/Syncthing/APK/{{app-name}}/{{apk-name}}"
    @echo "$HOME/Syncthing/APK/{{app-name}}/{{apk-name}}"
