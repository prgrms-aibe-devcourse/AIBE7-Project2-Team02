# Login Account Management Legacy Module

Archived standalone prototype for the original MatchEAT account implementation.

The production application uses `src/main/java/org/example/matcheat/domain/account` as its account and login implementation. This directory is intentionally excluded from the root Gradle build and must not be added to the root `settings.gradle` or referenced by the root `build.gradle`.

## Start here

- Historical implementation contract: `integration/ACCOUNT_SPEC.md`
- Isolation policy: `integration/INTEGRATION.md`
- PostgreSQL schema contract: `integration/auth-schema.sql`
- Environment variable template: `integration/env.example`

## Verify

Run from the repository root:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-account-module"
.\gradlew.bat -p modules\login-account-management clean test
```

This command verifies only the archived prototype. Production login regressions are covered by the root application's account and security tests.
