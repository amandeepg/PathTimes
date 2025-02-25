# Under The Hudson - Development Guidelines

## Build/Test Commands
- Android App:
  - Build: `./gradlew build`
  - Run all tests: `./gradlew test`
  - Debug tests: `./gradlew :app:testDebugUnitTest`
  - Release tests: `./gradlew :app:testReleaseUnitTest`
  - Single test: `./gradlew :app:testDebugUnitTest --tests "ca.amandeep.path.SpecificTest"`
  - Screenshot tests: `./gradlew recordPaparazziDebug`
  - Lint: `./gradlew lintKotlin`
- Serverless:
  - Deploy: `./deploy.sh`
  - Local testing: `serverless offline`
  - Package: `serverless package`

## Code Style
- **Android Architecture**: MVVM with Kotlin Flow and Jetpack Compose
- **UI**: 100% Jetpack Compose with Material 3
- **Kotlin Style**: Follow Kotlin official conventions
- **Python Style**: PEP 8 with type annotations and docstrings
- **Imports**: Group and organize by package; no wildcard imports
- **Naming**: 
  - Classes: PascalCase
  - Functions/Variables: camelCase (Kotlin) / snake_case (Python)
  - Constants: SCREAMING_SNAKE_CASE
- **Error Handling**: Use Result type (Kotlin) or exceptions with proper logging (Python)
- **Testing**: JUnit 4 with Truth assertions for Android; pytest for Python
- **Dependencies**: Use refreshVersions with '_' placeholder for versions

Keep code modular, well-tested, and follow PATH visual styling guidelines.