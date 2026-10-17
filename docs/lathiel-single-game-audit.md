# Lathiel single-game audit

Run 38746744687: one valid game, Anikthea won on turn 25. One startup AI decision timeout, zero in-game timeouts, zero unsupported cards. Kwia was recognized but not drawn or cast, so Lotus functionality remains untested.

Lathiel cast 10 spells, played 4 lands, cast commander once, and lost. Forge recorded Lathiel counter triggers targeting Mangara and Llanowar Elves.

The watchdog reported zero turn markers despite the completed game. It watched the Lab wrapper log rather than the Forge debug log where turn and action records are written. Fix the log source and add a turn-marker regression test.

Investigate startup decision timeout separately. Do not treat it as a deadlock. Do not infer pilot quality from one game. Pilot policy JSON is not runtime integrated.
