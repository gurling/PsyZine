# Adapted from PsyLog 0c29a70f. GPL-3.0-or-later. PsyZine modifications: 2026-10-11.
{
  description = "PsyZine Android Application";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      android-nixpkgs,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        # Minimal Android SDK for CI builds
        androidCompositionBuild = android-nixpkgs.sdk.${system} (
          sdkPkgs: with sdkPkgs; [
            cmdline-tools-latest
            build-tools-35-0-0
            platform-tools
            platforms-android-35
          ]
        );

        # Full Android SDK for development (includes emulator, NDK, etc.)
        androidCompositionDev = android-nixpkgs.sdk.${system} (
          sdkPkgs: with sdkPkgs; [
            cmdline-tools-latest
            build-tools-35-0-0
            platform-tools
            platforms-android-35
            emulator
            ndk-27-2-12479018
            cmake-3-22-1
          ]
        );

        androidSdkBuild = androidCompositionBuild;
        androidSdkDev = androidCompositionDev;

        # Java/Kotlin environment
        jdk = pkgs.jdk17;

        # Build environment (minimal for CI)
        buildInputs = with pkgs; [
          androidSdkBuild
          jdk
        ];

        # Dev environment (full)
        devInputs = with pkgs; [
          androidSdkDev
          jdk
        ];

      in
      {
        # Development shell
        devShells.default = pkgs.mkShell {
          buildInputs =
            devInputs
            ++ (with pkgs; [
              # Additional development tools
              android-studio
              scrcpy # For screen mirroring
              adb-sync
            ]);

          shellHook = ''
            export ANDROID_HOME=${androidSdkDev}/share/android-sdk
            export ANDROID_SDK_ROOT=${androidSdkDev}/share/android-sdk
            export JAVA_HOME=${jdk}
            export PATH=$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools:$PATH

            echo "Android development environment loaded"
            echo "ANDROID_HOME: $ANDROID_HOME"
            echo "JAVA_HOME: $JAVA_HOME"
            echo ""
            echo "Available Gradle commands:"
            echo "  ./gradlew assemblePlayRelease    - Build Play Store release APK"
            echo "  ./gradlew bundlePlayRelease      - Build Play Store release AAB"
            echo "  ./gradlew assembleDirectRelease  - Build Direct release APK"
            echo "  ./gradlew assemblePlayDebug      - Build Play Store debug APK"
            echo "  ./gradlew assembleDirectDebug    - Build Direct debug APK"
            echo "  ./gradlew assembleRelease        - Build all release APKs"
            echo "  ./gradlew assembleDebug          - Build all debug APKs"
            echo "  adb devices                   - List connected devices"
          '';

          ANDROID_HOME = "${androidSdkDev}/share/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdkDev}/share/android-sdk";
          JAVA_HOME = "${jdk}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdkDev}/share/android-sdk/build-tools/35.0.0/aapt2";
        };

        # CI shell (minimal, no Android Studio)
        devShells.ci = pkgs.mkShell {
          buildInputs = buildInputs;

          shellHook = ''
            export ANDROID_HOME=${androidSdkBuild}/share/android-sdk
            export ANDROID_SDK_ROOT=${androidSdkBuild}/share/android-sdk
            export JAVA_HOME=${jdk}

            echo "CI build environment loaded"
            echo "ANDROID_HOME: $ANDROID_HOME"
            echo "JAVA_HOME: $JAVA_HOME"
          '';

          ANDROID_HOME = "${androidSdkBuild}/share/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdkBuild}/share/android-sdk";
          JAVA_HOME = "${jdk}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdkBuild}/share/android-sdk/build-tools/35.0.0/aapt2";
        };
      }
    );
}
