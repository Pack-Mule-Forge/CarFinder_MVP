# Car Finder

A mobile app that remembers where you parked — tracks your phone's speed and
location in the background, detects when you've parked, and shows you the
direction and distance back when you return.

**Stack:** Kotlin Multiplatform for the core detection/guidance logic, Android
app UI in Jetpack Compose (currently Android-only; the shared module is
structured with future platforms in mind). Build with `./gradlew
assembleDebug` or open in Android Studio; `./gradlew test` runs the shared
and app test suites.

**Status:** Minimally Viable Product. It works surprisingly well for how
simple the core idea is, and it has known gaps — see the spec under
`specs/003-park-detect-guidance/` for what's explicitly in and out of scope.

## What it does

Car Finder is a straightforward but not trivial project. It's a mobile app
that tracks the phone's speed and location. When it determines that you've
parked, it saves that location — all done in the background. When you launch
the app and there's a stored location, the display shows the direction and
distance from your current position back to where you parked.

The algorithm is fairly simple in concept. When the phone is at driving
speed, it anticipates that you'll slow down to park. When you slow to
parking speed, it increases the sample rate, waiting for confirmation that
you've actually parked. Once parked, that location is stored. When the phone
reaches driving speed again, the parked location is cleared. This leaves a
lot of edge cases, but it works surprisingly well for an MVP.

## Why this exists, and how it was built

The experience of using GitHub Spec-Kit was exciting and overwhelming. I
have some history writing requirements, so this spec-driven-development
approach called to me. I worked with Claude to build out a solid
requirements document and constitution. The prompts that came out of those
sessions — covering the constitution, the plan, and the spec itself — are
kept in `Claude Prompts/`, exactly as they emerged from multiple rounds of
work with Claude in Cowork, in case they're useful to anyone else working
through the same process. But once I started the actual Spec-Kit process,
the volume of information and decisions it asked of me
became overwhelming — the biggest problem was losing track of the context
behind each individual issue. Claude's context window is a lot bigger than
mine.

My takeaway, and my recommendation to anyone trying Spec-Kit: have a second
AI on the side whose only job is to help you track, summarize, and explain
the tradeoffs behind each decision as it comes up. And don't let yourself
feel rushed by a blinking cursor. I used Claude's Cowork mode for this,
since it gave both of us direct visibility into the files and the repo to
validate actions and check error resolutions rather than taking anything on
faith. I named that role, the Overseer.

That turned out to matter more than I expected. Spec-Kit's own `analyze`
step is genuinely good at catching real problems — not just typos, but
actual bugs that would have shipped silently. Two examples that survived
into this repo's ledger (`specs/003-park-detect-guidance/analysis-findings.md`):
an early pass caught that closing the app didn't reliably end the
background process — `finishAndRemoveTask()` alone doesn't guarantee it, and
the pending-close signal had no way to survive an in-flight permission
prompt. Separately, in an earlier build, a background service's own code
comment claimed it would escalate its GPS sampling rate as you got close to
parking; it never actually did, so detection that was supposed to take
10-15 seconds quietly took closer to a minute. Neither of those failed a
test before analyze caught them — nothing was watching for them yet.

I ran the Spec-Kit process through several rounds of `analyze` → fix →
re-analyze to work through findings like these before I was willing to call
it clean. Two earlier attempts at this same feature are kept in
`_rerun_impl_stash/` for comparison — not because they were failures, but
because comparing independent builds against the same requirements is part
of how I tested whether the requirements themselves were good enough.

## What's next

There's a follow-on production version planned with some more fun features.

## More

Check out [packmuleforge.com](http://packmuleforge.com) (Coming Soon) for
more of my projects and progress.

## License

See [LICENSE](./LICENSE).
